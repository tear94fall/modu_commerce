package com.example.commerce.api.order

import com.example.commerce.api.common.CustomerRequired
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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
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

@Schema(description = "주문 한 줄(옵션과 수량)")
data class OrderLineRequest(
    @field:Schema(description = "주문할 옵션(SKU) id. 필수", example = "12")
    @field:NotNull(message = "옵션(skuId)을 골라 주세요.")
    val skuId: Long? = null,
    @field:Schema(description = "수량. 필수, 1~99. 같은 옵션이 여러 줄이면 합쳐서 재고를 확인한다", example = "1")
    @field:NotNull(message = "수량을 입력해 주세요.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    @field:Max(value = 99, message = "수량은 99 이하여야 합니다.")
    val quantity: Int? = null,
)

@Schema(description = "주문(결제) 요청. 결제는 모의라 만들자마자 결제 완료(PAID)가 된다.")
data class CreateOrderRequest(
    @field:Schema(description = "배송지 id(내 배송지). 필수", example = "1")
    @field:NotNull(message = "배송지를 골라 주세요.")
    val addressId: Long? = null,
    @field:Schema(description = "주문할 옵션·수량 목록. 필수, 한 줄 이상")
    @field:Valid
    @field:NotEmpty(message = "주문할 상품이 없습니다.")
    val items: List<OrderLineRequest>? = null,
    @field:Schema(description = "장바구니에서 주문했을 때 주문 뒤 지울 장바구니 줄 id. 선택. 내 것이 아니거나 없는 줄은 건너뛴다", example = "[5, 6]")
    val cartItemIds: List<Long>? = null,
    /** 결제에 쓸 포인트(1P = 1원). 상품 금액까지만. */
    @field:Schema(description = "쓸 포인트(1P = 1원). 선택, 기본 0. 쿠폰 할인 뒤 결제할 금액까지만 쓸 수 있다", example = "1000")
    @field:Min(value = 0, message = "사용 포인트는 0 이상이어야 합니다.")
    val usePoints: Long? = null,
    /** 쓸 쿠폰(내 쿠폰함의 id). */
    @field:Schema(description = "쓸 쿠폰(내 쿠폰함의 id). 선택", example = "3")
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

@Tag(
    name = "주문 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). " +
            "모두 계정 토큰(aud modu-commerce) 필요. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
@CustomerRequired
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val getOrdersUseCase: GetOrdersUseCase,
    private val getOrderUseCase: GetOrderUseCase,
) {
    @Operation(
        summary = "주문하기",
        description =
            "옵션 재고를 잠근 채 깎고 쿠폰·포인트를 적용해 결제 완료(PAID) 주문을 만든 뒤 201 과 Location 을 돌려준다. " +
                "쓴 장바구니 줄은 지운다. 배송지·옵션이 없으면 404, 재고 부족·판매 중지·쓸 수 없는 쿠폰·포인트 부족이나 한도 초과면 400(전부 되돌림), " +
                "포인트 서버를 못 부르면 503.",
    )
    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<OrderDetailResult> {
        val created = createOrderUseCase.execute(jwt.userId(), request.toCommand())
        return ResponseEntity.created(URI.create("/api/v1/orders/${created.id}")).body(created)
    }

    @Operation(summary = "주문 목록 조회", description = "내 주문을 최근 주문 순으로 한 페이지 돌려준다.")
    @GetMapping
    fun orders(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<OrderSummaryResult>> =
        ResponseEntity.ok(
            PageResponse.from(getOrdersUseCase.execute(jwt.userId(), page, size)) {
                it
            },
        )

    @Operation(
        summary = "주문 상세 조회",
        description = "주문 줄(주문 당시 이름·옵션·단가 스냅샷)과 줄마다 쓴 리뷰 id, 쿠폰·포인트 금액을 돌려준다. 내 주문이 아니거나 없으면 404.",
    )
    @GetMapping("/{id}")
    fun order(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "주문 id", example = "10")
        @PathVariable id: Long,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(getOrderUseCase.execute(jwt.userId(), id))

    @Operation(
        summary = "주문 취소",
        description =
            "결제 완료(PAID) 상태의 내 주문만 취소한다. 재고를 되돌리고 쓴 쿠폰·포인트를 돌려준다(포인트 환불은 멱등). " +
                "배송 중·완료·이미 취소면 400, 내 주문이 아니면 404.",
    )
    @PostMapping("/{id}/cancel")
    fun cancel(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "주문 id", example = "10")
        @PathVariable id: Long,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(cancelOrderUseCase.execute(jwt.userId(), id))
}
