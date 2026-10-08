package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Category
import com.example.commerce.application.domain.repository.rw.CategoryRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.usecase.command.CategoryCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 카테고리는 [Category.MAX_DEPTH]단계까지만. 규칙 위반은 IllegalArgumentException(→ 400). */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class CategoryCommandService(
    private val categoryRwRepository: CategoryRwRepository,
    private val productRwRepository: ProductRwRepository,
) {
    fun create(command: CategoryCommand): Category {
        val parent = command.parentId?.let { findActive(it) }
        require((parent?.depth() ?: 0) < Category.MAX_DEPTH) { "카테고리는 ${Category.MAX_DEPTH}단계까지만 만들 수 있습니다." }
        val sortOrder = command.sortOrder ?: nextSortOrder(parent?.id)
        return categoryRwRepository.save(Category.create(command.name, parent, sortOrder).decorate(command.icon, command.color))
    }

    /**
     * 이름·부모·순서·아이콘을 바꾼다. 부모를 바꾸면(옮기면) 하위 카테고리도 함께 따라간다 —
     * 자기 자신이나 하위 아래로는 못 옮기고, 옮긴 뒤 가장 깊은 하위가 [Category.MAX_DEPTH]단계를 넘으면 안 된다.
     * sortOrder 가 없으면 같은 부모에선 그대로, 다른 부모로 옮기면 새 형제들의 맨 뒤.
     */
    fun update(
        id: Long,
        command: CategoryCommand,
    ): Category {
        val category = findActive(id)
        val parent = command.parentId?.let { findActive(it) }
        val moved = parent?.id != category.parent?.id
        if (parent != null) {
            require(!parent.isSelfOrDescendantOf(id)) { "카테고리를 자기 자신이나 하위 카테고리 아래로 옮길 수 없습니다." }
        }
        if (moved) {
            val depthAfterMove = (parent?.depth() ?: 0) + height(id)
            require(depthAfterMove <= Category.MAX_DEPTH) {
                "옮기면 하위 카테고리까지 ${depthAfterMove}단계가 됩니다. 카테고리는 ${Category.MAX_DEPTH}단계까지만 만들 수 있습니다."
            }
        }
        val sortOrder = command.sortOrder ?: if (moved) nextSortOrder(parent?.id) else category.sortOrder
        category.update(command.name, parent, sortOrder)
        category.decorate(command.icon, command.color)
        return category
    }

    /**
     * 한 부모 아래 형제 순서를 [ids] 순서대로 0..n-1 로 다시 매긴다. [parentId] 가 null 이면 최상위.
     * [ids] 는 지금 형제 목록과 정확히 같은 집합이어야 한다(다른 사람이 그사이 바꿨으면 400).
     */
    fun reorder(
        parentId: Long?,
        ids: List<Long>,
    ) {
        parentId?.let { findActive(it) }
        val siblings = categoryRwRepository.findSiblings(parentId)
        require(ids.size == siblings.size && ids.toSet() == siblings.map { it.id }.toSet()) {
            "순서를 바꿀 카테고리 목록이 현재 목록과 다릅니다. 새로고침 후 다시 시도하세요."
        }
        val byId = siblings.associateBy { requireNotNull(it.id) }
        ids.forEachIndexed { index, id -> byId.getValue(id).reorder(index) }
        // 바로 이어서 읽는 트리 조회(ORDER BY sort_order)가 새 순서를 보게 한다.
        categoryRwRepository.flush()
    }

    fun delete(id: Long) {
        val category = findActive(id)
        require(categoryRwRepository.countChildren(id) == 0L) { "하위 카테고리가 있어 삭제할 수 없습니다." }
        require(productRwRepository.countByCategory(id) == 0L) { "상품이 있는 카테고리는 삭제할 수 없습니다." }
        category.delete()
    }

    fun findActive(id: Long): Category =
        categoryRwRepository.findById(id).orElse(null)?.takeUnless { it.isDeleted() }
            ?: run {
                logger.error { "Category not found: $id" }
                throw EntityNotFoundException("id: $id 에 해당하는 카테고리가 없습니다.")
            }

    private fun nextSortOrder(parentId: Long?): Int = categoryRwRepository.maxSortOrder(parentId)?.plus(1) ?: 0

    /** [id] 를 꼭대기로 하는 서브트리의 높이(자기만 있으면 1). */
    private fun height(id: Long): Int {
        val childrenByParent = categoryRwRepository.findAllByOrderBySortOrderAscIdAsc().groupBy { it.parent?.id }

        fun heightOf(
            nodeId: Long,
            seen: Set<Long>,
        ): Int =
            1 + (
                childrenByParent[nodeId]
                    .orEmpty()
                    .mapNotNull { it.id }
                    .filterNot { it in seen }
                    .maxOfOrNull { heightOf(it, seen + it) } ?: 0
            )
        return heightOf(id, setOf(id))
    }
}
