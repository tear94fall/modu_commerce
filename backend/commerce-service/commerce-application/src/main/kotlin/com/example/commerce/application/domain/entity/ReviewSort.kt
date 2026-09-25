package com.example.commerce.application.domain.entity

/** 앱 리뷰 목록 정렬. 쿼리 파라미터 값은 [param]. */
enum class ReviewSort(
    val param: String,
) {
    LATEST("latest"),
    HIGH("high"),
    LOW("low"),
    ;

    companion object {
        fun of(param: String?): ReviewSort = entries.firstOrNull { it.param == param } ?: LATEST
    }
}
