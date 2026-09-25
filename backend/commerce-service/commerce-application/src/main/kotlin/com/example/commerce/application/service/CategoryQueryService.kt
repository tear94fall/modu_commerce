package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Category
import com.example.commerce.application.domain.repository.ro.CategoryRoRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class CategoryQueryService(
    private val categoryRoRepository: CategoryRoRepository,
) {
    fun findAll(): List<Category> = categoryRoRepository.findAllByOrderBySortOrderAscIdAsc()

    fun findCategory(id: Long): Category =
        categoryRoRepository.findById(id)
            ?: run {
                logger.error { "Category not found: $id" }
                throw EntityNotFoundException("id: $id 에 해당하는 카테고리가 없습니다.")
            }

    /** 자기 자신과 하위 카테고리 id. 상위를 고르면 하위 상품까지 보이게 하는 데 쓴다. */
    fun subtreeIds(id: Long): List<Long> {
        val category = findCategory(id)
        return listOf(requireNotNull(category.id)) + categoryRoRepository.findChildIds(id)
    }
}
