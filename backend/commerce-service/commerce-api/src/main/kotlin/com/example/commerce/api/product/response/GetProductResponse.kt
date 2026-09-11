package com.example.commerce.api.product.response

import com.example.commerce.application.usecase.result.GetProductResult

data class GetProductResponse(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    val imageUrl: String?,
) {
    companion object {
        fun from(result: GetProductResult): GetProductResponse =
            GetProductResponse(
                id = result.id,
                name = result.name,
                description = result.description,
                price = result.price,
                imageUrl = result.imageUrl,
            )
    }
}
