package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Wishlist
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query

interface WishlistRoRepository : RoRepository<Wishlist, Long> {
    @Query("select w.product.id from Wishlist w where w.userId = :userId and w.product.id in :productIds")
    fun findWishedProductIds(
        userId: String,
        productIds: Collection<Long>,
    ): List<Long>

    fun existsByUserIdAndProductId(
        userId: String,
        productId: Long,
    ): Boolean

    /** 최근 찜부터. 삭제·숨김 상품은 상품 @SQLRestriction 과 서비스가 거른다. */
    fun findAllByUserIdOrderByIdDesc(
        userId: String,
        pageable: Pageable,
    ): Page<Wishlist>
}
