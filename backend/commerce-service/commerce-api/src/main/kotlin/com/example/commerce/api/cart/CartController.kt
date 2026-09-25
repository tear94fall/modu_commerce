package com.example.commerce.api.cart

import com.example.commerce.api.common.userId
import com.example.commerce.application.usecase.cart.AddCartItemUseCase
import com.example.commerce.application.usecase.cart.ChangeCartItemQuantityUseCase
import com.example.commerce.application.usecase.cart.GetCartUseCase
import com.example.commerce.application.usecase.cart.RemoveCartItemUseCase
import com.example.commerce.application.usecase.result.CartItemResult
import com.example.commerce.application.usecase.result.CartResult
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

data class AddCartItemRequest(
    @field:NotNull(message = "옵션(skuId)을 골라 주세요.")
    val skuId: Long? = null,
    @field:NotNull(message = "수량을 입력해 주세요.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    @field:Max(value = 99, message = "수량은 99 이하여야 합니다.")
    val quantity: Int? = null,
)

data class ChangeQuantityRequest(
    @field:NotNull(message = "수량을 입력해 주세요.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    @field:Max(value = 99, message = "수량은 99 이하여야 합니다.")
    val quantity: Int? = null,
)

@RestController
@RequestMapping("/api/v1/cart")
class CartController(
    private val getCartUseCase: GetCartUseCase,
    private val addCartItemUseCase: AddCartItemUseCase,
    private val changeCartItemQuantityUseCase: ChangeCartItemQuantityUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
) {
    @GetMapping
    fun cart(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CartResult> = ResponseEntity.ok(getCartUseCase.execute(jwt.userId()))

    @PostMapping("/items")
    fun add(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: AddCartItemRequest,
    ): ResponseEntity<CartItemResult> =
        ResponseEntity.ok(addCartItemUseCase.execute(jwt.userId(), requireNotNull(request.skuId), requireNotNull(request.quantity)))

    @PatchMapping("/items/{id}")
    fun changeQuantity(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @Valid @RequestBody request: ChangeQuantityRequest,
    ): ResponseEntity<CartItemResult> =
        ResponseEntity.ok(changeCartItemQuantityUseCase.execute(jwt.userId(), id, requireNotNull(request.quantity)))

    @DeleteMapping("/items/{id}")
    fun remove(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        removeCartItemUseCase.execute(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }
}
