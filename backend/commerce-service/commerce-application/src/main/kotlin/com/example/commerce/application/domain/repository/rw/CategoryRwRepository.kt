package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Category
import org.springframework.data.jpa.repository.Query

interface CategoryRwRepository : RwRepository<Category, Long> {
    /** 어드민 트리(master). 지운 카테고리는 엔티티의 @SQLRestriction 이 뺀다. */
    fun findAllByOrderBySortOrderAscIdAsc(): List<Category>

    @Query("select count(c) from Category c where c.parent.id = :parentId")
    fun countChildren(parentId: Long): Long

    /** 형제 목록(정렬 순). [parentId] 가 null 이면 최상위. */
    @Query("select c from Category c where (:parentId is null and c.parent is null) or c.parent.id = :parentId order by c.sortOrder, c.id")
    fun findSiblings(parentId: Long?): List<Category>

    /** 형제 중 가장 큰 정렬 순서. 형제가 없으면 null. */
    @Query("select max(c.sortOrder) from Category c where (:parentId is null and c.parent is null) or c.parent.id = :parentId")
    fun maxSortOrder(parentId: Long?): Int?
}
