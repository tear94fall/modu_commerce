package com.example.commerce.application.usecase.command

/** 백오피스 등급 한 줄. */
data class TierCommand(
    val code: String,
    val name: String,
    val color: String,
    val minAmount: Long,
    val earnRate: Int,
    val couponIds: List<Long>,
) {
    fun validate() {
        require(name.isNotBlank() && name.trim().length <= 20) { "$code: 등급 이름은 1~20자여야 합니다." }
        require(COLOR.matches(color)) { "$code: 색은 #RRGGBB 형식이어야 합니다." }
        require(minAmount >= 0) { "$code: 기준 금액은 0원 이상이어야 합니다." }
        require(earnRate in 0..20) { "$code: 적립률은 0~20% 사이여야 합니다." }
        require(couponIds.size <= 10) { "$code: 매월 쿠폰은 10개까지 고를 수 있습니다." }
    }

    companion object {
        private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
