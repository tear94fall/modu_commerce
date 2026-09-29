package com.example.commerce.application.usecase.order

import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.service.CustomerQueryService
import com.example.commerce.application.service.OrderCommandService
import com.example.commerce.application.service.OrderQueryService
import com.example.commerce.application.service.ReviewQueryService
import com.example.commerce.application.usecase.command.CreateOrderCommand
import com.example.commerce.application.usecase.result.OrderDetailResult
import com.example.commerce.application.usecase.result.OrderSummaryResult
import com.example.commerce.application.usecase.result.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateOrderUseCase(
    private val orderCommandService: OrderCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        userId: String,
        command: CreateOrderCommand,
    ): OrderDetailResult = OrderDetailResult.from(orderCommandService.create(userId, command))
}

@Component
class CancelOrderUseCase(
    private val orderCommandService: OrderCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        userId: String,
        orderId: Long,
    ): OrderDetailResult = OrderDetailResult.from(orderCommandService.cancel(userId, orderId))
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
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(orderId: Long): OrderDetailResult = OrderDetailResult.from(orderQueryService.find(orderId))
}

@Component
class ChangeOrderStatusUseCase(
    private val orderCommandService: OrderCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        orderId: Long,
        next: OrderStatus,
    ): OrderDetailResult = OrderDetailResult.from(orderCommandService.changeStatus(orderId, next))
}
