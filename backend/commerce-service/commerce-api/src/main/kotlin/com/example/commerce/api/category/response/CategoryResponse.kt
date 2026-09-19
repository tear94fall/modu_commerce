package com.example.commerce.api.category.response

import com.example.commerce.application.usecase.category.CategoryNodeResult
import com.example.commerce.application.usecase.result.CategoryResult

data class CategoryResponse(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val productCount: Long?,
    val children: List<CategoryResponse>,
) {
    companion object {
        fun from(result: CategoryResult): CategoryResponse =
            CategoryResponse(
                id = result.id,
                name = result.name,
                sortOrder = result.sortOrder,
                productCount = result.productCount,
                children = result.children.map(::from),
            )
    }
}

data class CategoryNodeResponse(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
) {
    companion object {
        fun from(result: CategoryNodeResult) =
            CategoryNodeResponse(id = result.id, name = result.name, parentId = result.parentId, sortOrder = result.sortOrder)
    }
}
