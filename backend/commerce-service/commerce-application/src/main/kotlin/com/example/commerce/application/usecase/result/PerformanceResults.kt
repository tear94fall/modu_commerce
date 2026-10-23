package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.CouponSource
import java.time.LocalDate

/**
 * 쿠폰 하나의 성과(백오피스 상세). 장 수는 user_coupon, 돈은 그 쿠폰을 쓴 주문(order)에서 — 취소된 주문은 할인·매출에서 뺀다.
 * [usedByDay] 는 표시 시간대의 날짜로 묶은 최근 [days]일(오래된 날부터).
 */
data class CouponPerformanceResult(
    val couponId: Long,
    val issued: Long,
    val used: Long,
    val available: Long,
    val expired: Long,
    /** 0~100, 발급이 없으면 null. */
    val usageRate: Int?,
    val bySource: List<CouponSourceCount>,
    /** 쿠폰을 쓴 주문(취소 제외). */
    val orders: Long,
    val cancelledOrders: Long,
    /** 깎아 준 돈(원, 취소 제외). */
    val discountTotal: Long,
    /** 그 주문들의 실결제액(상품 − 쿠폰 − 포인트, 원, 취소 제외). */
    val revenue: Long,
    /** 주문당 실결제액. 주문이 없으면 null. */
    val avgOrderAmount: Long?,
    val zone: String,
    val days: Int,
    val usedByDay: List<DayCount>,
)

data class CouponSourceCount(
    val source: CouponSource,
    val count: Long,
)

data class DayCount(
    val date: LocalDate,
    val count: Long,
)

/** 기획전·이벤트 하나의 성과. 종류에 맞는 것만 채운다(기획전 → exhibition, 출석 → attendance, 쿠폰 받기 → coupons). */
data class PromotionPerformanceResult(
    val promotionId: Long,
    val exhibition: ExhibitionPerformance?,
    val attendance: AttendancePerformance?,
    val coupons: List<EventCouponPerformance>?,
)

/** 기간 안에 결제된 주문 중 기획전 상품이 들어간 것(취소 제외). 상품 매출은 단가 × 수량. */
data class ExhibitionPerformance(
    val orders: Long,
    val cancelledOrders: Long,
    val units: Long,
    val sales: Long,
    val products: List<ExhibitionProductPerformance>,
)

data class ExhibitionProductPerformance(
    val productId: Long,
    val name: String,
    val units: Long,
    val sales: Long,
)

/** 참여자(서로 다른 회원)·출석 수·지급 포인트와 날짜별 출석 수(날짜 오름차순). */
data class AttendancePerformance(
    val participants: Long,
    val checks: Long,
    val rewardPoints: Long,
    val byDay: List<DayCount>,
)

/** 이벤트로 나간 쿠폰 한 장의 발급·사용 장 수(source = EVENT 만). */
data class EventCouponPerformance(
    val couponId: Long,
    val name: String,
    val issued: Long,
    val used: Long,
)
