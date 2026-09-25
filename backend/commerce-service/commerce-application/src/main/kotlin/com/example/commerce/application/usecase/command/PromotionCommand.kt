package com.example.commerce.application.usecase.command

import com.example.commerce.application.domain.entity.EventKind
import com.example.commerce.application.domain.entity.PromotionType
import java.time.LocalDate

/** 기획전·이벤트 만들기/고치기. 값은 앞뒤 공백을 걷은 뒤 검사한다. */
data class PromotionCommand(
    val type: PromotionType,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val bannerImageUrl: String?,
    val bannerColor: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val visible: Boolean,
    val sortOrder: Int,
    val productIds: List<Long>,
    val pointRuleCode: String?,
    val rewardPoints: Long?,
    /** 이벤트 종류. 이벤트가 아니면 무시. 없으면 출석 체크. */
    val eventKind: EventKind? = null,
    /** 기획전 쿠폰(0~10) 또는 쿠폰 이벤트의 쿠폰(1~10). */
    val couponIds: List<Long> = emptyList(),
) {
    fun validate() {
        require(title.isNotBlank() && title.length <= 60) { "제목은 1~60자로 입력하세요." }
        require((subtitle?.length ?: 0) <= 100) { "부제는 100자까지 입력할 수 있습니다." }
        require((description?.length ?: 0) <= 2000) { "설명은 2000자까지 입력할 수 있습니다." }
        require((bannerImageUrl?.length ?: 0) <= 500) { "배너 이미지 주소가 너무 깁니다." }
        require(bannerColor == null || COLOR.matches(bannerColor)) { "배너 색은 #RRGGBB 형식으로 입력하세요." }
        require(!endDate.isBefore(startDate)) { "종료일은 시작일보다 빠를 수 없습니다." }
        if (type == PromotionType.EXHIBITION) {
            require(productIds.isNotEmpty() && productIds.size <= 100) { "기획전 상품을 1~100개 고르세요." }
            require(productIds.toSet().size == productIds.size) { "같은 상품을 두 번 넣을 수 없습니다." }
        }
        require(rewardPoints == null || rewardPoints >= 0) { "보상 포인트는 0 이상이어야 합니다." }
        require(couponIds.size <= 10) { "쿠폰은 10개까지 넣을 수 있습니다." }
        require(couponIds.toSet().size == couponIds.size) { "같은 쿠폰을 두 번 넣을 수 없습니다." }
        if (type == PromotionType.EVENT && (eventKind ?: EventKind.ATTENDANCE) == EventKind.COUPON) {
            require(couponIds.isNotEmpty()) { "쿠폰 이벤트에 줄 쿠폰을 1~10개 고르세요." }
        }
    }

    companion object {
        private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")

        /** 빈 문자열은 없음(null)으로 본다. */
        fun blankToNull(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
