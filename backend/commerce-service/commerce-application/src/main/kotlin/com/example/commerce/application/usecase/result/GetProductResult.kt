package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Product

data class GetProductResult(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    val imageUrl: String?,
) {
    companion object {
        fun from(product: Product) =
            GetProductResult(
                id = requireNotNull(product.id) { "Product id must not be null when creating a result." },
                name = product.name,
                description = product.description,
                price = product.price,
                imageUrl = product.imageUrl,
            )
    }
}
