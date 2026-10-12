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
        // Spring Data 4 의 Page<T> 는 T : Any 라 바운드가 필요하다.
        fun <E : Any, T : Any> from(
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
