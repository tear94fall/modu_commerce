package com.example.commerce.application.usecase.cart

import com.example.commerce.application.service.CartCommandService
import com.example.commerce.application.service.CartQueryService
import com.example.commerce.application.usecase.result.CartItemResult
import com.example.commerce.application.usecase.result.CartResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetCartUseCase(
    private val cartQueryService: CartQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(userId: String): CartResult = CartResult.from(cartQueryService.items(userId))
}

@Component
class AddCartItemUseCase(
    private val cartCommandService: CartCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        userId: String,
        skuId: Long,
        quantity: Int,
    ): CartItemResult = CartItemResult.from(cartCommandService.add(userId, skuId, quantity))
}

@Component
class ChangeCartItemQuantityUseCase(
    private val cartCommandService: CartCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        userId: String,
        itemId: Long,
        quantity: Int,
    ): CartItemResult = CartItemResult.from(cartCommandService.changeQuantity(userId, itemId, quantity))
}

@Component
class RemoveCartItemUseCase(
    private val cartCommandService: CartCommandService,
) {
    fun execute(
        userId: String,
        itemId: Long,
    ) = cartCommandService.remove(userId, itemId)
}
