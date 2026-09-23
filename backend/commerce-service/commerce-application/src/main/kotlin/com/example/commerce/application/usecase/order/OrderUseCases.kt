package com.example.commerce.application.usecase.order

import com.example.commerce.application.domain.entity.OrderStatus
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
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        page: Int,
        size: Int,
    ): PageResult<OrderSummaryResult> = PageResult.from(orderQueryService.page(userId, page, size), OrderSummaryResult::from)
}

@Component
class GetOrderUseCase(
    private val orderQueryService: OrderQueryService,
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        orderId: Long,
    ): OrderDetailResult {
        val order = orderQueryService.own(userId, orderId)
        return OrderDetailResult.from(order, reviewQueryService.reviewIdsOf(order.items.map { requireNotNull(it.id) }))
    }
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
