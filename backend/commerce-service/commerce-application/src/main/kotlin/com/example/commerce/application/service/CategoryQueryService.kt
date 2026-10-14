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

    /** 자기 자신과 모든 하위(깊이 무관) 카테고리 id. 상위를 고르면 하위 상품까지 보이게 하는 데 쓴다. */
    fun subtreeIds(id: Long): List<Long> {
        val ids = mutableListOf(requireNotNull(findCategory(id).id))
        var level = listOf(ids.first())
        // 단계마다 한 번 조회한다(최대 MAX_DEPTH 번). 혹시 모를 순환에도 멈추게 본 id 는 다시 따라가지 않는다.
        while (level.isNotEmpty()) {
            level = categoryRoRepository.findChildIds(level).filterNot { it in ids }
            ids += level
        }
        return ids
    }
}
