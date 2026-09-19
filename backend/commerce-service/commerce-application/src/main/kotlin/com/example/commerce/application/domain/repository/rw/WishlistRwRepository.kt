package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Wishlist

interface WishlistRwRepository : RwRepository<Wishlist, Long> {
    fun findByUserIdAndProductId(
        userId: String,
        productId: Long,
    ): Wishlist?
}
