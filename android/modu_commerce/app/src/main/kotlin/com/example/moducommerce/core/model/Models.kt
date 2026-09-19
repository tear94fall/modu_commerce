package com.example.moducommerce.core.model

/** auth-service userinfo. */
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val picture: String = "",
)

data class Category(
    val id: Long,
    val name: String,
    val children: List<Category> = emptyList(),
)

data class ProductSummary(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val price: Long,
    val listPrice: Long?,
    val discountRate: Int,
    val soldOut: Boolean,
    val wished: Boolean,
)

data class OptionValue(val id: Long, val name: String)

data class OptionGroup(val id: Long, val name: String, val values: List<OptionValue>)

data class Sku(
    val id: Long,
    val optionValueIds: List<Long>,
    val optionLabel: String,
    val extraPrice: Long,
    val stock: Int,
) {
    val soldOut: Boolean get() = stock <= 0
}

data class ProductDetail(
    val id: Long,
    val name: String,
    val description: String,
    val detail: String?,
    val images: List<String>,
    val price: Long,
    val listPrice: Long?,
    val discountRate: Int,
    val soldOut: Boolean,
    val wished: Boolean,
    val wishCount: Long,
    val categoryPath: List<String>,
    val optionGroups: List<OptionGroup>,
    val skus: List<Sku>,
) {
    fun toSummary(): ProductSummary =
        ProductSummary(id, name, images.firstOrNull(), price, listPrice, discountRate, soldOut, wished)
}

/** 서버 `ProductSort.param` 과 같은 문자열. */
enum class ProductSort(val param: String) {
    LATEST("latest"),
    POPULAR("popular"),
    PRICE_ASC("priceAsc"),
    PRICE_DESC("priceDesc"),
}

data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
) {
    val hasNext: Boolean get() = number + 1 < totalPages
}
