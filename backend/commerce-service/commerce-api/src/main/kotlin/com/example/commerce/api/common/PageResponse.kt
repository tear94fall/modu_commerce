package com.example.commerce.api.common

import com.example.commerce.application.usecase.result.PageResult

/** 백오피스의 Page<T> 타입(content/totalElements/totalPages/number/size)과 같은 모양. */
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
) {
    companion object {
        fun <R, T> from(
            result: PageResult<R>,
            mapper: (R) -> T,
        ) = PageResponse(
            content = result.content.map(mapper),
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            number = result.number,
            size = result.size,
        )
    }
}
