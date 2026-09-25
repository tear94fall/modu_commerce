package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Category
import org.springframework.data.jpa.repository.Query

interface CategoryRoRepository : RoRepository<Category, Long> {
    fun findAllByOrderBySortOrderAscIdAsc(): List<Category>

    fun findById(id: Long): Category?

    @Query("select c.id from Category c where c.parent.id = :parentId")
    fun findChildIds(parentId: Long): List<Long>
}
