package com.example.commerce.api.product.response

import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.usecase.result.AdminProductSummaryResult
import com.example.commerce.application.usecase.result.OptionGroupResult
import com.example.commerce.application.usecase.result.ProductDetailResult
import com.example.commerce.application.usecase.result.ProductSummaryResult
import com.example.commerce.application.usecase.result.SkuResult

data class ProductSummaryResponse(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val price: Long,
    val listPrice: Long?,
    val discountRate: Int,
    val soldOut: Boolean,
    val wished: Boolean,
    val reviewCount: Long,
    val ratingAverage: Double,
) {
    companion object {
        fun from(r: ProductSummaryResult) =
            ProductSummaryResponse(
                r.id,
                r.name,
                r.imageUrl,
                r.price,
                r.listPrice,
                r.discountRate,
                r.soldOut,
                r.wished,
                r.reviewCount,
                r.ratingAverage,
            )
    }
}

data class AdminProductSummaryResponse(
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
        fun from(r: AdminProductSummaryResult) =
            AdminProductSummaryResponse(
                r.id,
                r.name,
                r.description,
                r.imageUrl,
                r.price,
                r.listPrice,
                r.status,
                r.totalStock,
                r.categoryId,
                r.categoryName,
            )
    }
}

data class OptionValueResponse(
    val id: Long,
    val name: String,
)

data class OptionGroupResponse(
    val id: Long,
    val name: String,
    val values: List<OptionValueResponse>,
) {
    companion object {
        fun from(r: OptionGroupResult) = OptionGroupResponse(r.id, r.name, r.values.map { OptionValueResponse(it.id, it.name) })
    }
}

data class SkuResponse(
    val id: Long,
    val optionValueIds: List<Long>,
    val optionLabel: String,
    val extraPrice: Long,
    val stock: Int,
) {
    companion object {
        fun from(r: SkuResult) = SkuResponse(r.id, r.optionValueIds, r.optionLabel, r.extraPrice, r.stock)
    }
}

/** 앱 상세와 어드민 단건·등록·수정 응답. */
data class ProductDetailResponse(
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
    val reviewCount: Long,
    val ratingAverage: Double,
    val categoryId: Long?,
    val categoryPath: List<String>,
    val optionGroups: List<OptionGroupResponse>,
    val skus: List<SkuResponse>,
) {
    companion object {
        fun from(r: ProductDetailResult) =
            ProductDetailResponse(
                id = r.id,
                name = r.name,
                description = r.description,
                detail = r.detail,
                imageUrl = r.imageUrl,
                images = r.images,
                price = r.price,
                listPrice = r.listPrice,
                discountRate = r.discountRate,
                status = r.status,
                soldOut = r.soldOut,
                wished = r.wished,
                wishCount = r.wishCount,
                reviewCount = r.reviewCount,
                ratingAverage = r.ratingAverage,
                categoryId = r.categoryId,
                categoryPath = r.categoryPath,
                optionGroups = r.optionGroups.map(OptionGroupResponse::from),
                skus = r.skus.map(SkuResponse::from),
            )
    }
}
