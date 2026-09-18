package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Product
import org.springframework.data.jpa.repository.Query

interface ProductRwRepository : RwRepository<Product, Long> {
    /** @SQLRestriction 을 우회해 삭제된 행까지 센다. 시더가 "한 번이라도 상품이 있었는지" 볼 때 쓴다. */
    @Query(value = "select count(*) from products", nativeQuery = true)
    fun countIncludingDeleted(): Long
}
