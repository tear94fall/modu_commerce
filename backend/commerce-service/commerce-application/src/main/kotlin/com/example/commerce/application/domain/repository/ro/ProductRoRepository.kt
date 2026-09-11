package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Product

interface ProductRoRepository :
    RoRepository<Product, Long>,
    ProductCustomRepository {
    fun findAllByOrderByIdAsc(): List<Product>

    fun findById(id: Long): Product?
}
