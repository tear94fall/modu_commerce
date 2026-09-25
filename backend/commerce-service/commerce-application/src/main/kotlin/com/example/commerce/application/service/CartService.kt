package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.ProductSku
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.repository.ro.CartItemRoRepository
import com.example.commerce.application.domain.repository.rw.CartItemRwRepository
import com.example.commerce.application.domain.repository.rw.ProductSkuRwRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class CartQueryService(
    private val cartItemRoRepository: CartItemRoRepository,
) {
    fun items(userId: String): List<CartItem> = cartItemRoRepository.findAllByUserIdOrderByIdDesc(userId)
}

@Service
@Transactional(transactionManager = "rwTransactionManager")
class CartCommandService(
    private val cartItemRwRepository: CartItemRwRepository,
    private val productSkuRwRepository: ProductSkuRwRepository,
) {
    /** 같은 SKU 가 있으면 수량을 더한다. 재고보다 많이 담는 것은 허용하되(주문 때 막는다) 99 는 넘지 못한다. */
    fun add(
        userId: String,
        skuId: Long,
        quantity: Int,
    ): CartItem {
        val sku = sellingSku(skuId)
        val existing = cartItemRwRepository.findByUserIdAndSkuId(userId, skuId)
        if (existing != null) {
            existing.add(quantity)
            return existing
        }
        return cartItemRwRepository.save(CartItem(userId = userId, sku = sku, quantity = quantity).also { it.changeQuantity(quantity) })
    }

    fun changeQuantity(
        userId: String,
        itemId: Long,
        quantity: Int,
    ): CartItem = own(userId, itemId).also { it.changeQuantity(quantity) }

    fun remove(
        userId: String,
        itemId: Long,
    ) {
        cartItemRwRepository.delete(own(userId, itemId))
    }

    /** 주문이 끝난 줄을 지운다. 남의 줄·없는 줄은 조용히 건너뛴다. */
    fun removeAll(
        userId: String,
        itemIds: Collection<Long>,
    ) {
        if (itemIds.isEmpty()) return
        cartItemRwRepository.deleteAll(cartItemRwRepository.findAllByIdInAndUserId(itemIds, userId))
    }

    private fun own(
        userId: String,
        itemId: Long,
    ): CartItem =
        cartItemRwRepository.findByIdAndUserId(itemId, userId) ?: throw EntityNotFoundException("id: $itemId 에 해당하는 장바구니 항목이 없습니다.")

    private fun sellingSku(skuId: Long): ProductSku {
        val sku = productSkuRwRepository.findById(skuId).orElse(null) ?: throw EntityNotFoundException("id: $skuId 에 해당하는 옵션이 없습니다.")
        require(!sku.product.isDeleted() && sku.product.status == ProductStatus.SELLING) { "지금은 판매하지 않는 상품입니다." }
        return sku
    }
}
