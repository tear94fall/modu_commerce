package com.example.commerce.application.domain.entity

/** 앱 목록 정렬. 모르는 값은 최신순. */
enum class ProductSort(
    val param: String,
) {
    LATEST("latest"),
    PRICE_ASC("priceAsc"),
    PRICE_DESC("priceDesc"),
    POPULAR("popular"),
    ;

    companion object {
        fun fromParam(value: String?): ProductSort = entries.firstOrNull { it.param == value } ?: LATEST
    }
}
