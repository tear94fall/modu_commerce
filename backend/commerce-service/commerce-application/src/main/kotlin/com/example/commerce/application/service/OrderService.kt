package com.example.commerce.application.service

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.rw.OrderRwRepository
import com.example.commerce.application.domain.repository.rw.ProductSkuRwRepository
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.usecase.command.CreateOrderCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class OrderQueryService(
    private val orderRoRepository: OrderRoRepository,
) {
    fun page(
        userId: String,
        page: Int,
        size: Int,
    ): Page<Order> = orderRoRepository.findAllByUserIdOrderByIdDesc(userId, pageOf(page, size))

    /** 레플리카에 없으면 null — 방금 만든 주문일 수 있으니 부르는 쪽이 master 로 다시 본다([OrderCommandService.readOwn]). */
    fun findOwn(
        userId: String,
        id: Long,
    ): Order? = orderRoRepository.findByIdAndUserId(id, userId)

    fun adminPage(
        status: OrderStatus?,
        orderNo: String?,
        page: Int,
        size: Int,
    ): Page<Order> = orderRoRepository.searchAdminPage(status, orderNo, pageOf(page, size))

    fun find(id: Long): Order = orderRoRepository.findById(id) ?: throw notFound(id)

    private fun pageOf(
        page: Int,
        size: Int,
    ) = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, ProductQueryService.MAX_PAGE_SIZE))

    companion object {
        fun notFound(id: Long) = EntityNotFoundException("id: $id 에 해당하는 주문이 없습니다.")
    }
}

/** 화면에 보인 결제 금액과 서버가 계산한 금액이 다르다(그사이 가격·쿠폰·재고가 바뀜). 409 PRICE_CHANGED 와 새 금액으로 답한다. */
class PriceChangedException(
    val paymentAmount: Long,
) : RuntimeException(MESSAGE) {
    companion object {
        const val CODE = "PRICE_CHANGED"
        const val MESSAGE = "가격이 바뀌었어요. 결제 금액을 다시 확인해 주세요."
    }
}

/**
 * 주문 생성은 SKU 행을 잠근 채 재고를 깎고 스냅샷을 만든다. 결제는 모의라 생성과 동시에 PAID.
 * 취소(사용자·관리자)는 주문 행을 먼저 잠그고(동시 취소는 한 번만 된다) SKU 를 id 순으로 잠가 재고를 되돌린다.
 *
 * 포인트: DB 작업을 다 마친 뒤(커밋 직전) point-service 에 차감을 요청한다. 차감 직전에 아웃박스 SPEND_GUARD 를 따로 커밋해 두므로,
 * 차감 뒤 커밋이 실패해도(드묾) 릴레이가 주문이 없는 것을 보고 차감을 되돌린다. 취소 환불은 취소와 같은 트랜잭션의 REFUND 행으로 남기고
 * 커밋 뒤 보낸다 — point-service 가 죽어 있어도 취소는 된다([PointOutboxService]).
 *
 * 시각은 주입받은 [clock] 의 UTC, 주문번호 날짜는 한국 날짜다.
 */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class OrderCommandService(
    private val orderRwRepository: OrderRwRepository,
    private val productSkuRwRepository: ProductSkuRwRepository,
    private val addressCommandService: AddressCommandService,
    private val cartCommandService: CartCommandService,
    private val pointGateway: PointGateway,
    private val pointOutboxService: PointOutboxService,
    private val couponUseService: CouponUseService,
    private val purchaseEarnService: PurchaseEarnService,
    private val clock: Clock,
) {
    fun create(
        userId: String,
        command: CreateOrderCommand,
    ): Order {
        require(command.items.isNotEmpty()) { "주문할 상품이 없습니다." }
        val address = addressCommandService.own(userId, command.addressId)
        val order = Order.create(userId, address, newOrderNo(), TierPeriods.utcNow(clock), command.idempotencyKey)
        // 같은 SKU 가 두 줄로 오면 합친다. 잠금 순서는 id 순으로 고정해 교착을 막는다.
        val lines =
            command.items
                .groupBy { it.skuId }
                .mapValues { (_, l) -> l.sumOf { it.quantity } }
                .toSortedMap()
        lines.forEach { (skuId, quantity) ->
            val sku = productSkuRwRepository.findByIdForUpdate(skuId) ?: throw EntityNotFoundException("id: $skuId 에 해당하는 옵션이 없습니다.")
            require(!sku.product.isDeleted() && sku.product.status == ProductStatus.SELLING) { "지금은 판매하지 않는 상품입니다: ${sku.product.name}" }
            sku.decreaseStock(quantity)
            order.addItem(sku, quantity)
        }
        val coupon = command.userCouponId?.let { couponUseService.applyTo(order, it) }
        order.usePoints(command.usePoints)
        // 화면이 본 금액과 다르면 포인트를 건드리기 전에 멈춘다(전부 롤백).
        command.expectedPaymentAmount?.let { expected ->
            if (expected != order.paymentAmount()) throw PriceChangedException(order.paymentAmount())
        }
        val saved = orderRwRepository.saveAndFlush(order)
        coupon?.let { couponUseService.markUsed(it, saved) }
        cartCommandService.removeAll(userId, command.cartItemIds)
        if (saved.pointAmount > 0) {
            pointOutboxService.guardSpend(saved)
            pointGateway.spend(userId, saved.pointAmount, saved.pointSpendRefId(), "주문 결제 ${saved.orderNo}")
        }
        logger.info {
            "order ${saved.orderNo} created by $userId: ${saved.totalAmount}원 (쿠폰 ${saved.couponDiscount}, 포인트 ${saved.pointAmount})"
        }
        return saved
    }

    /**
     * 내 주문을 master 에서 읽어 트랜잭션 안에서 [map] 한다(주문 줄 지연 로딩). 없거나 남의 것이면 404.
     * 결제 직후 주문 상세로 넘어가면 레플리카에는 아직 주문이 없을 수 있어 그때 쓴다.
     */
    @Transactional(transactionManager = "rwTransactionManager", readOnly = true)
    fun <T> readOwn(
        userId: String,
        orderId: Long,
        map: (Order) -> T,
    ): T = map(orderRwRepository.findByIdAndUserId(orderId, userId) ?: throw OrderQueryService.notFound(orderId))

    /** 같은 회원·같은 Idempotency-Key 로 만든 주문을 master 에서 찾아 [map] 한다. 없으면 null. */
    @Transactional(transactionManager = "rwTransactionManager", readOnly = true)
    fun <T> readByIdempotencyKey(
        userId: String,
        key: String,
        map: (Order) -> T,
    ): T? = orderRwRepository.findByUserIdAndIdempotencyKey(userId, key)?.let(map)

    /** 주문을 master 에서 읽어 [map] 한다(취소·상태 변경 커밋 뒤 응답). */
    @Transactional(transactionManager = "rwTransactionManager", readOnly = true)
    fun <T> read(
        orderId: Long,
        map: (Order) -> T,
    ): T = map(orderRwRepository.findById(orderId).orElse(null) ?: throw OrderQueryService.notFound(orderId))

    fun cancel(
        userId: String,
        orderId: Long,
    ): Order {
        val order = orderRwRepository.findByIdAndUserIdForUpdate(orderId, userId) ?: throw OrderQueryService.notFound(orderId)
        order.cancel(TierPeriods.utcNow(clock))
        restock(order)
        couponUseService.restore(order)
        refundPoints(order)
        return order
    }

    fun changeStatus(
        orderId: Long,
        next: OrderStatus,
    ): Order {
        val order = orderRwRepository.findByIdForUpdate(orderId) ?: throw OrderQueryService.notFound(orderId)
        order.transition(next, TierPeriods.utcNow(clock))
        if (next == OrderStatus.CANCELLED) {
            restock(order)
            couponUseService.restore(order)
            refundPoints(order)
        }
        // 배송 완료: 커밋 뒤 구매 적립(고객의 지금 등급 적립률).
        if (next == OrderStatus.DELIVERED) purchaseEarnService.afterCommit(orderId)
        return order
    }

    /** 결제에 쓴 포인트를 돌려준다. 취소 트랜잭션 안에 REFUND 행만 넣고, 보내기는 커밋 뒤에 한다. */
    private fun refundPoints(order: Order) {
        if (order.pointAmount <= 0) return
        pointOutboxService.refundOnCancel(order)
    }

    /** SKU 를 id 순으로 잠그고 되돌린다(주문 생성·상품 저장과 같은 순서). 주문 행은 호출자가 먼저 잠갔다. */
    private fun restock(order: Order) {
        order.items.sortedBy { requireNotNull(it.sku.id) }.forEach { item ->
            productSkuRwRepository.findByIdForUpdate(requireNotNull(item.sku.id))?.increaseStock(item.quantity)
        }
    }

    /** 한국 날짜 + 난수. 이미 있는 번호면 다시 뽑는다(최대 [ORDER_NO_TRIES]번). 유니크 제약이 최종 방어다. */
    private fun newOrderNo(): String = nextOrderNo(LocalDate.now(clock.withZone(TierPeriods.KST))) { orderRwRepository.existsByOrderNo(it) }

    companion object {
        const val ORDER_NO_TRIES = 3

        /** [today](한국 날짜)의 새 주문번호. [exists] 가 true 인 번호는 버리고 다시 뽑는다. [ORDER_NO_TRIES]번 모두 겹치면 IllegalStateException. */
        fun nextOrderNo(
            today: LocalDate,
            generate: (LocalDate) -> String = { Order.newOrderNo(it) },
            exists: (String) -> Boolean,
        ): String {
            repeat(ORDER_NO_TRIES) {
                val no = generate(today)
                if (!exists(no)) return no
            }
            throw IllegalStateException("주문번호를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
    }
}
