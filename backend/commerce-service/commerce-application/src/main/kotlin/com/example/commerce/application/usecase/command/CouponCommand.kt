package com.example.commerce.application.usecase.command

import com.example.commerce.application.domain.entity.CouponScope
import com.example.commerce.application.domain.entity.DiscountType
import java.time.LocalDate

/** 쿠폰 만들기/고치기. 문자열은 앞뒤 공백을 걷은 값이다. */
data class CouponCommand(
    val name: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long,
    val scope: CouponScope,
    val scopeIds: List<Long>,
    val issueStart: LocalDate,
    val issueEnd: LocalDate,
    val validUntil: LocalDate?,
    val validDays: Int?,
    val totalQuantity: Long?,
    val code: String?,
    val downloadable: Boolean,
    val active: Boolean,
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 40) { "쿠폰 이름은 1~40자로 입력하세요." }
        require((description?.length ?: 0) <= 200) { "설명은 200자까지 입력할 수 있습니다." }
        when (discountType) {
            DiscountType.FIXED -> require(discountValue >= 1) { "할인 금액은 1원 이상이어야 합니다." }
            DiscountType.PERCENT -> {
                require(discountValue in 1..90) { "할인율은 1~90% 사이로 입력하세요." }
                require(maxDiscount == null || maxDiscount >= 1) { "최대 할인 금액은 1원 이상이어야 합니다." }
            }
        }
        require(minOrderAmount >= 0) { "최소 주문 금액은 0원 이상이어야 합니다." }
        if (scope != CouponScope.ALL) {
            require(scopeIds.isNotEmpty() && scopeIds.size <= 100) { "적용할 카테고리나 상품을 1~100개 고르세요." }
        }
        require(!issueEnd.isBefore(issueStart)) { "발급 종료일은 시작일보다 빠를 수 없습니다." }
        require((validUntil == null) != (validDays == null)) { "사용 기한은 날짜 또는 받은 날부터 며칠 중 하나만 정하세요." }
        require(validDays == null || validDays in 1..3650) { "사용 기간은 1~3650일 사이로 입력하세요." }
        require(validUntil == null || !validUntil.isBefore(issueStart)) { "사용 기한은 발급 시작일보다 빠를 수 없습니다." }
        require(totalQuantity == null || totalQuantity >= 1) { "총 수량은 1장 이상이어야 합니다." }
        require(code == null || CODE.matches(code)) { "쿠폰 코드는 영문 대문자·숫자 4~20자로 입력하세요." }
    }

    companion object {
        private val CODE = Regex("^[A-Z0-9]{4,20}$")
    }
}
