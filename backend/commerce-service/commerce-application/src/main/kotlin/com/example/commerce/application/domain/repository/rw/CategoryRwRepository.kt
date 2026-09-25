package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Category
import org.springframework.data.jpa.repository.Query

interface CategoryRwRepository : RwRepository<Category, Long> {
    @Query("select count(c) from Category c where c.parent.id = :parentId")
    fun countChildren(parentId: Long): Long
}
