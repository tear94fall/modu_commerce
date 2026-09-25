package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Product

interface ProductRoRepository :
    RoRepository<Product, Long>,
    ProductCustomRepository {
    fun findAllByOrderByIdAsc(): List<Product>

    fun findById(id: Long): Product?

    /** 지운 상품은 @SQLRestriction 으로 빠진다. 순서는 보장하지 않는다. */
    fun findAllByIdIn(ids: Collection<Long>): List<Product>
}
