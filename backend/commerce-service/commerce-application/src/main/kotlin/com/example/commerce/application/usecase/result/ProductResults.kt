package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductStatus

/** 앱 목록 한 칸. */
data class ProductSummaryResult(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val price: Long,
    val listPrice: Long?,
    val discountRate: Int,
    val soldOut: Boolean,
    val wished: Boolean,
) {
    companion object {
        fun from(
            product: Product,
            wished: Boolean,
        ) = ProductSummaryResult(
            id = requireNotNull(product.id),
            name = product.name,
            imageUrl = product.imageUrl,
            price = product.price,
            listPrice = product.listPrice,
            discountRate = product.discountRate(),
            soldOut = product.isSoldOut(),
            wished = wished,
        )
    }
}

/** 어드민 목록 한 줄. */
data class AdminProductSummaryResult(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val price: Long,
    val listPrice: Long?,
    val status: ProductStatus,
    val totalStock: Int,
    val categoryId: Long?,
    val categoryName: String?,
) {
    companion object {
        fun from(product: Product) =
            AdminProductSummaryResult(
                id = requireNotNull(product.id),
                name = product.name,
                description = product.description,
                imageUrl = product.imageUrl,
                price = product.price,
                listPrice = product.listPrice,
                status = product.status,
                totalStock = product.totalStock(),
                categoryId = product.category?.id,
                categoryName = product.category?.name,
            )
    }
}

data class OptionValueResult(
    val id: Long,
    val name: String,
)

data class OptionGroupResult(
    val id: Long,
    val name: String,
    val values: List<OptionValueResult>,
)

data class SkuResult(
    val id: Long,
    val optionValueIds: List<Long>,
    val optionLabel: String,
    val extraPrice: Long,
    val stock: Int,
)

/** 상세. 앱과 어드민이 같이 쓴다(어드민은 wished=false). 컬렉션을 읽으므로 트랜잭션 안에서 만든다. */
data class ProductDetailResult(
    val id: Long,
    val name: String,
    val description: String,
    val detail: String?,
    val imageUrl: String?,
    val images: List<String>,
    val price: Long,
    val listPrice: Long?,
    val discountRate: Int,
    val status: ProductStatus,
    val soldOut: Boolean,
    val wished: Boolean,
    val wishCount: Long,
    val categoryId: Long?,
    val categoryPath: List<String>,
    val optionGroups: List<OptionGroupResult>,
    val skus: List<SkuResult>,
) {
    companion object {
        fun from(
            product: Product,
            wished: Boolean = false,
        ) = ProductDetailResult(
            id = requireNotNull(product.id),
            name = product.name,
            description = product.description,
            detail = product.detail,
            imageUrl = product.imageUrl,
            images = product.images.map { it.url },
            price = product.price,
            listPrice = product.listPrice,
            discountRate = product.discountRate(),
            status = product.status,
            soldOut = product.isSoldOut(),
            wished = wished,
            wishCount = product.wishCount,
            categoryId = product.category?.id,
            categoryPath =
                product.category
                    ?.path()
                    ?.map { it.name }
                    .orEmpty(),
            optionGroups =
                product.optionGroups.map { group ->
                    OptionGroupResult(
                        id = requireNotNull(group.id),
                        name = group.name,
                        values = group.values.map { OptionValueResult(requireNotNull(it.id), it.name) },
                    )
                },
            skus =
                product.skus.map { sku ->
                    SkuResult(
                        id = requireNotNull(sku.id),
                        optionValueIds = sku.optionValues.sortedBy { it.group.sortOrder }.map { requireNotNull(it.id) },
                        optionLabel = sku.optionLabel(),
                        extraPrice = sku.extraPrice,
                        stock = sku.stock,
                    )
                },
        )
    }
}
