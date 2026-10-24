package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductSku

/**
 * 재고 경고. 판매 중 상품만 본다(숨긴·지운 상품은 팔리지 않으니 경고도 없다).
 * - [soldOut]: 모든 SKU 가 0 인 상품(찜 많은 순)
 * - [lowStock]: 그 밖의 상품에서 재고가 [low] 이하인 SKU(적은 순)
 * - [overstock]: 재고가 [high] 이상인 SKU(많은 순)
 * 모든 줄에 최근 [salesDays]일 판매 수량([sold])을 붙여, 부족이 급한지·과다가 진짜 안 팔리는 건지 가늠하게 한다.
 */
data class StockAlertResult(
    val low: Int,
    val high: Int,
    val salesDays: Int,
    val sellingProducts: Long,
    val soldOut: List<StockProductResult>,
    val lowStock: List<StockSkuResult>,
    val overstock: List<StockSkuResult>,
)

data class StockProductResult(
    val productId: Long,
    val name: String,
    val imageUrl: String?,
    val wishCount: Long,
    val skuCount: Int,
    /** 최근 salesDays 일 동안 이 상품(모든 SKU)이 팔린 수량 */
    val sold: Int,
) {
    companion object {
        fun from(
            p: Product,
            sold: Int,
        ) = StockProductResult(requireNotNull(p.id), p.name, p.imageUrl, p.wishCount, p.skus.size, sold)
    }
}

data class StockSkuResult(
    val skuId: Long,
    val productId: Long,
    val productName: String,
    val imageUrl: String?,
    /** "블랙 / M". 옵션이 없으면 빈 문자열. */
    val optionLabel: String,
    val stock: Int,
    val wishCount: Long,
    /** 최근 salesDays 일 동안 이 SKU 가 팔린 수량 */
    val sold: Int,
) {
    companion object {
        fun from(
            s: ProductSku,
            sold: Int,
        ) = StockSkuResult(
            skuId = requireNotNull(s.id),
            productId = requireNotNull(s.product.id),
            productName = s.product.name,
            imageUrl = s.product.imageUrl,
            optionLabel = s.optionLabel(),
            stock = s.stock,
            wishCount = s.product.wishCount,
            sold = sold,
        )
    }
}
