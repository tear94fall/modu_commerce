package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Product

interface ProductRwRepository : RwRepository<Product, Long>
