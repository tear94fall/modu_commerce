package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Category
import com.example.commerce.application.domain.repository.rw.CategoryRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.usecase.command.CategoryCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 카테고리는 2단계까지만. 규칙 위반은 IllegalArgumentException(→ 400). */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class CategoryCommandService(
    private val categoryRwRepository: CategoryRwRepository,
    private val productRwRepository: ProductRwRepository,
) {
    fun create(command: CategoryCommand): Category {
        val parent = command.parentId?.let { resolveParent(it) }
        return categoryRwRepository.save(Category.create(command.name, parent, command.sortOrder).decorate(command.icon, command.color))
    }

    fun update(
        id: Long,
        command: CategoryCommand,
    ): Category {
        val category = findActive(id)
        val parent = command.parentId?.let { resolveParent(it) }
        require(parent?.id != id) { "카테고리를 자기 자신 아래에 둘 수 없습니다." }
        if (parent != null) {
            require(categoryRwRepository.countChildren(id) == 0L) { "하위 카테고리가 있는 카테고리는 다른 카테고리 아래로 옮길 수 없습니다." }
        }
        category.update(command.name, parent, command.sortOrder)
        category.decorate(command.icon, command.color)
        return category
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

    private fun resolveParent(parentId: Long): Category {
        val parent = findActive(parentId)
        require(parent.isRoot()) { "카테고리는 2단계까지만 만들 수 있습니다." }
        return parent
    }
}
