package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Category

/** 트리 한 노드. [productCount] 는 어드민용(그 카테고리에 직접 속한 상품 수), 앱에는 null. [depth] 는 최상위가 1. */
data class CategoryResult(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val productCount: Long?,
    val children: List<CategoryResult>,
    val icon: String? = null,
    val color: String? = null,
    val depth: Int = 1,
) {
    companion object {
        /** (sortOrder, id) 로 정렬된 평면 목록을 깊이 제한 없이 상위 → 하위 트리로 만든다. 단계마다 입력 순서를 지킨다. */
        fun tree(
            categories: List<Category>,
            productCount: (Category) -> Long? = { null },
        ): List<CategoryResult> {
            val childrenByParent = categories.filter { it.parent != null }.groupBy { it.parent!!.id }

            fun node(
                category: Category,
                depth: Int,
            ): CategoryResult =
                CategoryResult(
                    id = requireNotNull(category.id),
                    name = category.name,
                    sortOrder = category.sortOrder,
                    productCount = productCount(category),
                    children = childrenByParent[category.id].orEmpty().map { node(it, depth + 1) },
                    icon = category.icon,
                    color = category.color,
                    depth = depth,
                )
            return categories.filter { it.parent == null }.map { node(it, 1) }
        }
    }
}
