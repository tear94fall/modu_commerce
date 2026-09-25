package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Category

/** 트리 한 노드. [productCount] 는 어드민용(그 카테고리에 직접 속한 상품 수), 앱에는 null. */
data class CategoryResult(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val productCount: Long?,
    val children: List<CategoryResult>,
    val icon: String? = null,
    val color: String? = null,
) {
    companion object {
        /** 정렬된 평면 목록을 상위 → 하위 트리로 만든다. */
        fun tree(
            categories: List<Category>,
            productCount: (Category) -> Long? = { null },
        ): List<CategoryResult> {
            val childrenByParent = categories.filter { it.parent != null }.groupBy { it.parent!!.id }

            fun node(category: Category): CategoryResult =
                CategoryResult(
                    id = requireNotNull(category.id),
                    name = category.name,
                    sortOrder = category.sortOrder,
                    productCount = productCount(category),
                    children = childrenByParent[category.id].orEmpty().map(::node),
                    icon = category.icon,
                    color = category.color,
                )
            return categories.filter { it.parent == null }.map(::node)
        }
    }
}
