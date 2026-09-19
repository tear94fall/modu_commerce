package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.rw.OrderRwRepository
import com.example.commerce.application.domain.repository.rw.ProductSkuRwRepository
import com.example.commerce.application.usecase.command.CreateOrderCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    fun own(
        userId: String,
        id: Long,
    ): Order = orderRoRepository.findByIdAndUserId(id, userId) ?: throw notFound(id)

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

/**
 * 주문 생성은 SKU 행을 잠근 채 재고를 깎고 스냅샷을 만든다. 결제는 모의라 생성과 동시에 PAID.
 * 취소(사용자·관리자)는 잠근 채 재고를 되돌린다.
 */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class OrderCommandService(
    private val orderRwRepository: OrderRwRepository,
    private val productSkuRwRepository: ProductSkuRwRepository,
    private val addressCommandService: AddressCommandService,
    private val cartCommandService: CartCommandService,
) {
    fun create(
        userId: String,
        command: CreateOrderCommand,
    ): Order {
        require(command.items.isNotEmpty()) { "주문할 상품이 없습니다." }
        val address = addressCommandService.own(userId, command.addressId)
        val order = Order.create(userId, address)
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
        val saved = orderRwRepository.saveAndFlush(order)
        cartCommandService.removeAll(userId, command.cartItemIds)
        logger.info { "order ${saved.orderNo} created by $userId: ${saved.totalAmount}원" }
        return saved
    }

    fun cancel(
        userId: String,
        orderId: Long,
    ): Order {
        val order = orderRwRepository.findByIdAndUserId(orderId, userId) ?: throw OrderQueryService.notFound(orderId)
        order.cancel()
        restock(order)
        return order
    }

    fun changeStatus(
        orderId: Long,
        next: OrderStatus,
    ): Order {
        val order = orderRwRepository.findById(orderId).orElse(null) ?: throw OrderQueryService.notFound(orderId)
        order.transition(next)
        if (next == OrderStatus.CANCELLED) restock(order)
        return order
    }

    private fun restock(order: Order) {
        order.items.sortedBy { requireNotNull(it.sku.id) }.forEach { item ->
            productSkuRwRepository.findByIdForUpdate(requireNotNull(item.sku.id))?.increaseStock(item.quantity)
        }
    }
}
