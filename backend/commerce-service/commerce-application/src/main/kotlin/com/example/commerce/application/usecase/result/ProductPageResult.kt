package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Product
import org.springframework.data.domain.Page

/** Spring 의 PageImpl 을 그대로 직렬화하지 않도록 필요한 값만 옮긴다. */
data class ProductPageResult(
    val content: List<GetProductResult>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
) {
    companion object {
        fun from(page: Page<Product>) =
            ProductPageResult(
                content = page.content.map(GetProductResult::from),
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                number = page.number,
                size = page.size,
            )
    }
}
