package com.example.commerce.api.order

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.application.usecase.command.CreateOrderCommand
import com.example.commerce.application.usecase.command.OrderLineCommand
import com.example.commerce.application.usecase.order.CancelOrderUseCase
import com.example.commerce.application.usecase.order.CreateOrderUseCase
import com.example.commerce.application.usecase.order.GetOrderUseCase
import com.example.commerce.application.usecase.order.GetOrdersUseCase
import com.example.commerce.application.usecase.result.OrderDetailResult
import com.example.commerce.application.usecase.result.OrderSummaryResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

data class OrderLineRequest(
    @field:NotNull(message = "옵션(skuId)을 골라 주세요.")
    val skuId: Long? = null,
    @field:NotNull(message = "수량을 입력해 주세요.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    @field:Max(value = 99, message = "수량은 99 이하여야 합니다.")
    val quantity: Int? = null,
)

data class CreateOrderRequest(
    @field:NotNull(message = "배송지를 골라 주세요.")
    val addressId: Long? = null,
    @field:Valid
    @field:NotEmpty(message = "주문할 상품이 없습니다.")
    val items: List<OrderLineRequest>? = null,
    val cartItemIds: List<Long>? = null,
    /** 결제에 쓸 포인트(1P = 1원). 상품 금액까지만. */
    @field:Min(value = 0, message = "사용 포인트는 0 이상이어야 합니다.")
    val usePoints: Long? = null,
    /** 쓸 쿠폰(내 쿠폰함의 id). */
    val userCouponId: Long? = null,
) {
    fun toCommand() =
        CreateOrderCommand(
            addressId = requireNotNull(addressId),
            items = requireNotNull(items).map { OrderLineCommand(requireNotNull(it.skuId), requireNotNull(it.quantity)) },
            cartItemIds = cartItemIds.orEmpty(),
            usePoints = usePoints ?: 0,
            userCouponId = userCouponId,
        )
}

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val getOrdersUseCase: GetOrdersUseCase,
    private val getOrderUseCase: GetOrderUseCase,
) {
    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<OrderDetailResult> {
        val created = createOrderUseCase.execute(jwt.userId(), request.toCommand())
        return ResponseEntity.created(URI.create("/api/v1/orders/${created.id}")).body(created)
    }

    @GetMapping
    fun orders(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<OrderSummaryResult>> =
        ResponseEntity.ok(
            PageResponse.from(getOrdersUseCase.execute(jwt.userId(), page, size)) {
                it
            },
        )

    @GetMapping("/{id}")
    fun order(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(getOrderUseCase.execute(jwt.userId(), id))

    @PostMapping("/{id}/cancel")
    fun cancel(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(cancelOrderUseCase.execute(jwt.userId(), id))
}
