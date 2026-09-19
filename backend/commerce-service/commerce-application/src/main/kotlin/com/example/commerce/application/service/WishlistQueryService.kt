package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.Wishlist
import com.example.commerce.application.domain.repository.ro.WishlistRoRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class WishlistQueryService(
    private val wishlistRoRepository: WishlistRoRepository,
) {
    /** 목록 한 페이지의 상품 중 내가 찜한 것의 id. */
    fun wishedIds(
        userId: String,
        productIds: Collection<Long>,
    ): Set<Long> = if (productIds.isEmpty()) emptySet() else wishlistRoRepository.findWishedProductIds(userId, productIds).toSet()

    fun isWished(
        userId: String,
        productId: Long,
    ): Boolean = wishlistRoRepository.existsByUserIdAndProductId(userId, productId)

    fun findPage(
        userId: String,
        page: Int,
        size: Int,
    ): Page<Wishlist> =
        wishlistRoRepository.findAllByUserIdOrderByIdDesc(
            userId,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, ProductQueryService.MAX_PAGE_SIZE)),
        )
}
