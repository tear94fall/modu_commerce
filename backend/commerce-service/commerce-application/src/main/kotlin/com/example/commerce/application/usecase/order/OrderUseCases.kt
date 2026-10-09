package com.example.commerce.application.usecase.order

import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.service.CustomerQueryService
import com.example.commerce.application.service.OrderCommandService
import com.example.commerce.application.service.OrderQueryService
import com.example.commerce.application.service.PointRefundStatusQueryService
import com.example.commerce.application.service.ReviewQueryService
import com.example.commerce.application.usecase.command.CreateOrderCommand
import com.example.commerce.application.usecase.result.CreateOrderOutcome
import com.example.commerce.application.usecase.result.OrderDetailResult
import com.example.commerce.application.usecase.result.OrderSummaryResult
import com.example.commerce.application.usecase.result.PageResult
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 주문 생성. Idempotency-Key 가 있으면 먼저 master 에서 같은 회원·같은 키의 주문을 찾아 그대로 돌려준다(created=false → 200).
 * 동시에 같은 키로 두 번 오면 하나는 유니크 제약(user_id, idempotency_key)에 걸려 통째로 롤백되고(포인트 차감 전), 먼저 만든 주문을 돌려준다.
 * 트랜잭션은 여기서 직접 연다 — 제약 위반으로 롤백된 뒤 새 트랜잭션에서 기존 주문을 읽어야 한다.
 */
@Component
class CreateOrderUseCase(
    private val orderCommandService: OrderCommandService,
    @Qualifier("rwTransactionManager") transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun execute(
        userId: String,
        command: CreateOrderCommand,
    ): CreateOrderOutcome {
        val key = command.idempotencyKey
        if (key != null) existing(userId, key)?.let { return CreateOrderOutcome(it, created = false) }
        return try {
            CreateOrderOutcome(
                requireNotNull(tx.execute { OrderDetailResult.from(orderCommandService.create(userId, command)) }),
                created = true,
            )
        } catch (e: DataIntegrityViolationException) {
            val found = key?.let { existing(userId, it) } ?: throw e
            CreateOrderOutcome(found, created = false)
        }
    }

    private fun existing(
        userId: String,
        key: String,
    ): OrderDetailResult? = orderCommandService.readByIdempotencyKey(userId, key) { OrderDetailResult.from(it) }
}

/**
 * 취소. 취소 트랜잭션이 커밋되면 포인트 환불을 바로 한 번 보내므로(아웃박스), 응답은 커밋 뒤 master 에서 다시 읽어 환불 상태를 싣는다.
 */
@Component
class CancelOrderUseCase(
    private val orderCommandService: OrderCommandService,
    private val pointRefundStatusQueryService: PointRefundStatusQueryService,
    @Qualifier("rwTransactionManager") transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun execute(
        userId: String,
        orderId: Long,
    ): OrderDetailResult {
        tx.executeWithoutResult { orderCommandService.cancel(userId, orderId) }
        return orderCommandService.readOwn(userId, orderId) {
            OrderDetailResult.from(it, pointRefundStatus = pointRefundStatusQueryService.ofMaster(it))
        }
    }
}

@Component
class GetOrdersUseCase(
    private val orderQueryService: OrderQueryService,
    private val customerQueryService: CustomerQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        page: Int,
        size: Int,
    ): PageResult<OrderSummaryResult> {
        val tier = customerQueryService.currentTier(userId)
        return PageResult.from(orderQueryService.page(userId, page, size)) { OrderSummaryResult.from(it, tier) }
    }
}

@Component
class GetOrderUseCase(
    private val orderQueryService: OrderQueryService,
    private val orderCommandService: OrderCommandService,
    private val reviewQueryService: ReviewQueryService,
    private val customerQueryService: CustomerQueryService,
    private val pointRefundStatusQueryService: PointRefundStatusQueryService,
) {
    /**
     * 레플리카에서 읽고, 없으면 master 에서 한 번 더 본다. 앱은 결제하자마자 이 주문 상세로 넘어오는데
     * 복제 지연 동안 레플리카에는 주문이 없다(404 가 나면 "주문을 찾을 수 없습니다"가 뜬다). 정말 없는 주문만 404.
     */
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        orderId: Long,
    ): OrderDetailResult {
        val order =
            orderQueryService.findOwn(userId, orderId)
                ?: return orderCommandService.readOwn(userId, orderId) { detailOf(it, userId) }
        return detailOf(order, userId)
    }

    private fun detailOf(
        order: Order,
        userId: String,
    ): OrderDetailResult =
        OrderDetailResult.from(
            order,
            reviewQueryService.reviewIdsOf(order.items.map { requireNotNull(it.id) }),
            customerQueryService.currentTier(userId),
            pointRefundStatusQueryService.of(order),
        )
}

@Component
class SearchAdminOrdersUseCase(
    private val orderQueryService: OrderQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        status: OrderStatus?,
        orderNo: String?,
        page: Int,
        size: Int,
    ): PageResult<OrderSummaryResult> = PageResult.from(orderQueryService.adminPage(status, orderNo, page, size), OrderSummaryResult::from)
}

@Component
class GetAdminOrderUseCase(
    private val orderQueryService: OrderQueryService,
    private val pointRefundStatusQueryService: PointRefundStatusQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(orderId: Long): OrderDetailResult =
        orderQueryService.find(orderId).let { OrderDetailResult.from(it, pointRefundStatus = pointRefundStatusQueryService.of(it)) }
}

/** 관리자 상태 변경. 취소면 커밋 뒤 환불을 바로 보내므로 응답은 커밋 뒤 master 에서 다시 읽는다([CancelOrderUseCase] 와 같다). */
@Component
class ChangeOrderStatusUseCase(
    private val orderCommandService: OrderCommandService,
    private val pointRefundStatusQueryService: PointRefundStatusQueryService,
    @Qualifier("rwTransactionManager") transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun execute(
        orderId: Long,
        next: OrderStatus,
    ): OrderDetailResult {
        tx.executeWithoutResult { orderCommandService.changeStatus(orderId, next) }
        return orderCommandService.read(
            orderId,
        ) { OrderDetailResult.from(it, pointRefundStatus = pointRefundStatusQueryService.ofMaster(it)) }
    }
}
