package com.example.commerce.application.usecase.result

import java.time.LocalDate

/** 백오피스 홈 "오늘의 숫자" 중 커머스 부분. [days] 는 오래된 날부터 오늘까지. */
data class AdminStatsResult(
    val zone: String,
    val days: List<AdminStatsDay>,
)

/**
 * 그 날 결제된 주문 수·실결제액(상품 − 쿠폰 − 포인트, 원)·쿠폰을 쓴 주문 수와, 그 날 취소된 주문 수.
 * 취소는 결제한 날이 아니라 취소한 날에 센다(취소율은 화면에서 orders 대비).
 */
data class AdminStatsDay(
    val date: LocalDate,
    val orders: Int,
    val amount: Long,
    val couponUsed: Int,
    val cancelled: Int,
)
