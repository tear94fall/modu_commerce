package com.example.commerce.api.product.response

import com.example.commerce.application.usecase.result.ProductPageResult

/** 백오피스의 Page<T> 타입(content/totalElements/totalPages/number/size)과 같은 모양. */
data class ProductPageResponse(
    val content: List<GetProductResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
) {
    companion object {
        fun from(result: ProductPageResult) =
            ProductPageResponse(
                content = result.content.map(GetProductResponse::from),
                totalElements = result.totalElements,
                totalPages = result.totalPages,
                number = result.number,
                size = result.size,
            )
    }
}
