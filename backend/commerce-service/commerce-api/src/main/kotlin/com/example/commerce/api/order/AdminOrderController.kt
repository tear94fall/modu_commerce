package com.example.commerce.api.order

import com.example.commerce.api.common.PageResponse
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.usecase.order.ChangeOrderStatusUseCase
import com.example.commerce.application.usecase.order.GetAdminOrderUseCase
import com.example.commerce.application.usecase.order.SearchAdminOrdersUseCase
import com.example.commerce.application.usecase.result.OrderDetailResult
import com.example.commerce.application.usecase.result.OrderSummaryResult
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ChangeStatusRequest(
    @field:NotNull(message = "바꿀 상태를 골라 주세요.")
    val status: OrderStatus? = null,
)

@RestController
@RequestMapping("/api-admin/v1/orders")
class AdminOrderController(
    private val searchAdminOrdersUseCase: SearchAdminOrdersUseCase,
    private val getAdminOrderUseCase: GetAdminOrderUseCase,
    private val changeOrderStatusUseCase: ChangeOrderStatusUseCase,
) {
    @GetMapping
    fun orders(
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<OrderSummaryResult>> =
        ResponseEntity.ok(
            PageResponse.from(searchAdminOrdersUseCase.execute(status, q, page, size)) {
                it
            },
        )

    @GetMapping("/{id}")
    fun order(
        @PathVariable id: Long,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(getAdminOrderUseCase.execute(id))

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: ChangeStatusRequest,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(changeOrderStatusUseCase.execute(id, requireNotNull(request.status)))
}
