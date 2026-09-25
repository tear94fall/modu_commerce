package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.OrderItem
import com.example.commerce.application.domain.entity.Review

interface ReviewRwRepository : RwRepository<Review, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Review?

    fun existsByOrderItemId(orderItemId: Long): Boolean
}

interface OrderItemRwRepository : RwRepository<OrderItem, Long>
