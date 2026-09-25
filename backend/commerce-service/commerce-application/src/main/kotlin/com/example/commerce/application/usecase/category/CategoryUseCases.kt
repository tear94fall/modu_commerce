package com.example.commerce.application.usecase.category

import com.example.commerce.application.domain.entity.Category
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.service.CategoryCommandService
import com.example.commerce.application.service.CategoryQueryService
import com.example.commerce.application.usecase.command.CategoryCommand
import com.example.commerce.application.usecase.result.CategoryResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetCategoriesUseCase(
    private val categoryQueryService: CategoryQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(): List<CategoryResult> = CategoryResult.tree(categoryQueryService.findAll())
}

/** 어드민 트리. 상품 수는 master 에서 센다(방금 등록한 상품이 바로 보이게). */
@Component
class GetAdminCategoriesUseCase(
    private val categoryQueryService: CategoryQueryService,
    private val productRwRepository: ProductRwRepository,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(): List<CategoryResult> =
        CategoryResult.tree(categoryQueryService.findAll()) { productRwRepository.countByCategory(requireNotNull(it.id)) }
}

/** 등록·수정 응답은 자식 없는 노드 하나다. */
data class CategoryNodeResult(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
    val icon: String? = null,
    val color: String? = null,
) {
    companion object {
        fun from(category: Category) =
            CategoryNodeResult(
                id = requireNotNull(category.id),
                name = category.name,
                parentId = category.parent?.id,
                sortOrder = category.sortOrder,
                icon = category.icon,
                color = category.color,
            )
    }
}

@Component
class CreateCategoryUseCase(
    private val categoryCommandService: CategoryCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(command: CategoryCommand): CategoryNodeResult = CategoryNodeResult.from(categoryCommandService.create(command))
}

@Component
class UpdateCategoryUseCase(
    private val categoryCommandService: CategoryCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        id: Long,
        command: CategoryCommand,
    ): CategoryNodeResult = CategoryNodeResult.from(categoryCommandService.update(id, command))
}

@Component
class DeleteCategoryUseCase(
    private val categoryCommandService: CategoryCommandService,
) {
    fun execute(id: Long) = categoryCommandService.delete(id)
}
