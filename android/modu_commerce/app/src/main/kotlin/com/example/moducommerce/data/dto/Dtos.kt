package com.example.moducommerce.data.dto

import com.example.moducommerce.core.model.Category
import com.example.moducommerce.core.model.OptionGroup
import com.example.moducommerce.core.model.OptionValue
import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.model.ProductDetail
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.core.model.Sku
import com.example.moducommerce.core.model.UserProfile
import com.google.gson.annotations.SerializedName

/** auth-service /oauth2/token 응답. */
data class TokenResponseDto(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0,
)

data class UserInfoDto(
    val name: String? = null,
    val email: String? = null,
    val picture: String? = null,
) {
    fun toModel() = UserProfile(name = name.orEmpty(), email = email.orEmpty(), picture = picture.orEmpty())
}

data class CategoryDto(
    val id: Long = 0,
    val name: String? = null,
    val children: List<CategoryDto>? = null,
) {
    fun toModel(): Category = Category(id = id, name = name.orEmpty(), children = children.orEmpty().map { it.toModel() })
}

data class PageDto<T>(
    val content: List<T>? = null,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,
) {
    fun <R> toModel(mapper: (T) -> R): Page<R> = Page(content.orEmpty().map(mapper), totalElements, totalPages, number)
}

data class ProductSummaryDto(
    val id: Long = 0,
    val name: String? = null,
    val imageUrl: String? = null,
    val price: Long = 0,
    val listPrice: Long? = null,
    val discountRate: Int = 0,
    val soldOut: Boolean = false,
    val wished: Boolean = false,
) {
    fun toModel() = ProductSummary(id, name.orEmpty(), imageUrl, price, listPrice, discountRate, soldOut, wished)
}

data class OptionValueDto(val id: Long = 0, val name: String? = null)

data class OptionGroupDto(val id: Long = 0, val name: String? = null, val values: List<OptionValueDto>? = null)

data class SkuDto(
    val id: Long = 0,
    val optionValueIds: List<Long>? = null,
    val optionLabel: String? = null,
    val extraPrice: Long = 0,
    val stock: Int = 0,
)

data class ProductDetailDto(
    val id: Long = 0,
    val name: String? = null,
    val description: String? = null,
    val detail: String? = null,
    val images: List<String>? = null,
    val price: Long = 0,
    val listPrice: Long? = null,
    val discountRate: Int = 0,
    val soldOut: Boolean = false,
    val wished: Boolean = false,
    val wishCount: Long = 0,
    val categoryPath: List<String>? = null,
    val optionGroups: List<OptionGroupDto>? = null,
    val skus: List<SkuDto>? = null,
) {
    fun toModel() = ProductDetail(
        id = id,
        name = name.orEmpty(),
        description = description.orEmpty(),
        detail = detail,
        images = images.orEmpty(),
        price = price,
        listPrice = listPrice,
        discountRate = discountRate,
        soldOut = soldOut,
        wished = wished,
        wishCount = wishCount,
        categoryPath = categoryPath.orEmpty(),
        optionGroups = optionGroups.orEmpty().map { g ->
            OptionGroup(g.id, g.name.orEmpty(), g.values.orEmpty().map { OptionValue(it.id, it.name.orEmpty()) })
        },
        skus = skus.orEmpty().map { Sku(it.id, it.optionValueIds.orEmpty(), it.optionLabel.orEmpty(), it.extraPrice, it.stock) },
    )
}
