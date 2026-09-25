package com.example.commerce.application.usecase.result

import org.springframework.data.domain.Page

/** Spring 의 PageImpl 을 그대로 직렬화하지 않도록 필요한 값만 옮긴다. */
data class PageResult<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
) {
    companion object {
        fun <E, T> from(
            page: Page<E>,
            mapper: (E) -> T,
        ): PageResult<T> =
            PageResult(
                content = page.content.map(mapper),
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                number = page.number,
                size = page.size,
            )
    }
}
