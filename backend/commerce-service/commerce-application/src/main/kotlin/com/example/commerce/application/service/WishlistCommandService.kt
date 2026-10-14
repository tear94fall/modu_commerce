package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.Wishlist
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.domain.repository.rw.WishlistRwRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 찜 추가·해제는 멱등이다. 상품의 찜 수 캐시도 같이 맞춘다(원자적 UPDATE — 동시에 찜해도 수를 잃지 않는다). */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class WishlistCommandService(
    private val wishlistRwRepository: WishlistRwRepository,
    private val productCommandService: ProductCommandService,
    private val productRwRepository: ProductRwRepository,
) {
    fun add(
        userId: String,
        productId: Long,
    ) {
        val product = productCommandService.findActive(productId)
        if (wishlistRwRepository.findByUserIdAndProductId(userId, productId) != null) return
        wishlistRwRepository.save(Wishlist(userId = userId, product = product))
        productRwRepository.incrementWishCount(requireNotNull(product.id))
        product.increaseWishCount()
    }

    fun remove(
        userId: String,
        productId: Long,
    ) {
        val product = productCommandService.findActive(productId)
        val wish = wishlistRwRepository.findByUserIdAndProductId(userId, productId) ?: return
        wishlistRwRepository.delete(wish)
        productRwRepository.decrementWishCount(requireNotNull(product.id))
        product.decreaseWishCount()
    }
}
