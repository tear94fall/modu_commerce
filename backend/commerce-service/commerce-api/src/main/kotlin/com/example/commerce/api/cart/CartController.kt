package com.example.commerce.api.cart

import com.example.commerce.api.common.CustomerRequired
import com.example.commerce.api.common.userId
import com.example.commerce.application.usecase.cart.AddCartItemUseCase
import com.example.commerce.application.usecase.cart.ChangeCartItemQuantityUseCase
import com.example.commerce.application.usecase.cart.GetCartUseCase
import com.example.commerce.application.usecase.cart.RemoveCartItemUseCase
import com.example.commerce.application.usecase.result.CartItemResult
import com.example.commerce.application.usecase.result.CartResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Schema(description = "장바구니 담기 요청")
data class AddCartItemRequest(
    @field:Schema(description = "담을 옵션(SKU) id. 필수", example = "12")
    @field:NotNull(message = "옵션(skuId)을 골라 주세요.")
    val skuId: Long? = null,
    @field:Schema(description = "담을 수량. 필수, 1~99. 이미 담긴 옵션이면 기존 수량에 더하고 합이 99 를 넘으면 400", example = "1")
    @field:NotNull(message = "수량을 입력해 주세요.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    @field:Max(value = 99, message = "수량은 99 이하여야 합니다.")
    val quantity: Int? = null,
)

@Schema(description = "장바구니 수량 변경 요청")
data class ChangeQuantityRequest(
    @field:Schema(description = "바꿀 수량(더하는 값이 아니라 새 수량). 필수, 1~99", example = "2")
    @field:NotNull(message = "수량을 입력해 주세요.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    @field:Max(value = 99, message = "수량은 99 이하여야 합니다.")
    val quantity: Int? = null,
)

@Tag(
    name = "장바구니 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). " +
            "모두 계정 토큰(aud modu-commerce) 필요. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
@CustomerRequired
@RestController
@RequestMapping("/api/v1/cart")
class CartController(
    private val getCartUseCase: GetCartUseCase,
    private val addCartItemUseCase: AddCartItemUseCase,
    private val changeCartItemQuantityUseCase: ChangeCartItemQuantityUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
) {
    @Operation(
        summary = "장바구니 조회",
        description =
            "담은 줄을 최근 담은 순으로 돌려준다. 숨김·삭제된 상품이나 재고보다 많이 담은 줄은 합계(totalAmount)에서 빠진다(화면은 회색 처리).",
    )
    @GetMapping
    fun cart(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CartResult> = ResponseEntity.ok(getCartUseCase.execute(jwt.userId()))

    @Operation(
        summary = "장바구니 담기",
        description =
            "옵션(SKU)을 담는다. 같은 옵션이 이미 있으면 수량을 더한다. 재고보다 많이 담는 것은 허용하고 주문 때 막는다. " +
                "없는 옵션이면 404, 판매 중이 아닌 상품이거나 수량이 1~99 를 벗어나면 400.",
    )
    @PostMapping("/items")
    fun add(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: AddCartItemRequest,
    ): ResponseEntity<CartItemResult> =
        ResponseEntity.ok(addCartItemUseCase.execute(jwt.userId(), requireNotNull(request.skuId), requireNotNull(request.quantity)))

    @Operation(
        summary = "장바구니 수량 변경",
        description = "내 장바구니 줄의 수량을 새 값으로 바꾼다. 내 줄이 아니거나 없으면 404, 수량이 1~99 를 벗어나면 400.",
    )
    @PatchMapping("/items/{id}")
    fun changeQuantity(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "장바구니 줄 id", example = "5")
        @PathVariable id: Long,
        @Valid @RequestBody request: ChangeQuantityRequest,
    ): ResponseEntity<CartItemResult> =
        ResponseEntity.ok(changeCartItemQuantityUseCase.execute(jwt.userId(), id, requireNotNull(request.quantity)))

    @Operation(summary = "장바구니 줄 삭제", description = "내 장바구니 줄을 지우고 204. 내 줄이 아니거나 없으면 404.")
    @DeleteMapping("/items/{id}")
    fun remove(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "장바구니 줄 id", example = "5")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        removeCartItemUseCase.execute(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }
}
