package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Category
import org.springframework.data.jpa.repository.Query

interface CategoryRwRepository : RwRepository<Category, Long> {
    /** 어드민 트리(master). 지운 카테고리는 엔티티의 @SQLRestriction 이 뺀다. */
    fun findAllByOrderBySortOrderAscIdAsc(): List<Category>

    @Query("select count(c) from Category c where c.parent.id = :parentId")
    fun countChildren(parentId: Long): Long
}
