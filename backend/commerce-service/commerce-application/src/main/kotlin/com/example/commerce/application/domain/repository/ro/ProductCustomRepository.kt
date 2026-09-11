package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Product

interface ProductCustomRepository {
    /** 이름 또는 설명에 keyword 가 들어간 상품을 id 순으로 */
    fun search(keyword: String): List<Product>
}
