package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.Customer
import com.example.commerce.application.domain.entity.CustomerStatus
import com.example.commerce.application.domain.entity.EarnStatus
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.domain.entity.TierChangeReason
import com.example.commerce.application.domain.entity.TierHistory
import com.example.commerce.application.domain.entity.TierRun
import com.example.commerce.application.domain.entity.TierRunReason
import com.example.commerce.application.domain.entity.TierRunStatus
import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDateTime

/** 등급 요약. [earnRate] 는 퍼센트(3 = 3%). */
data class TierSummaryResult(
    val code: String,
    val name: String,
    val color: String,
    val earnRate: Int,
    val minAmount: Long,
) {
    companion object {
        fun from(t: Tier) = TierSummaryResult(t.code, t.name, t.color, t.earnRate, t.minAmount)
    }
}

data class RollingResult(
    val amount: Long,
    val expectedTier: TierSummaryResult,
    val nextTier: TierSummaryResult?,
    val amountToNext: Long?,
)

/** 앱 GET /me/customer. */
data class CustomerMeResult(
    val userId: String,
    val joinedAt: LocalDateTime,
    val termsAgreedAt: LocalDateTime?,
    val privacyAgreedAt: LocalDateTime?,
    val termsVersion: String?,
    val tier: TierSummaryResult,
    val basisAmount: Long,
    val rolling: RollingResult,
    val periodLabel: String,
)

data class MonthlyCouponResult(
    val name: String,
    val discountLabel: String,
) {
    companion object {
        fun from(c: Coupon) = MonthlyCouponResult(c.name, c.discountLabel())
    }
}

/** 앱 GET /tiers. 등급 요약 필드에 매월 쿠폰을 더한다(JSON 에서는 한 단계로 펼친다). */
data class PublicTierResult(
    @get:JsonUnwrapped
    val tier: TierSummaryResult,
    val monthlyCoupons: List<MonthlyCouponResult>,
)

data class TierCouponResult(
    val id: Long,
    val name: String,
    val discountLabel: String,
) {
    companion object {
        fun from(c: Coupon) = TierCouponResult(requireNotNull(c.id), c.name, c.discountLabel())
    }
}

data class AdminTierResult(
    val code: String,
    val name: String,
    val color: String,
    val minAmount: Long,
    val earnRate: Int,
    val sortOrder: Int,
    val coupons: List<TierCouponResult>,
    val customerCount: Long,
) {
    companion object {
        fun from(
            t: Tier,
            coupons: List<Coupon>,
            customerCount: Long,
        ) = AdminTierResult(
            t.code,
            t.name,
            t.color,
            t.minAmount,
            t.earnRate,
            t.sortOrder,
            coupons.map(TierCouponResult::from),
            customerCount,
        )
    }
}

data class AdminCustomerResult(
    val userId: String,
    val name: String?,
    val email: String?,
    val status: CustomerStatus,
    val tier: TierSummaryResult,
    val basisAmount: Long,
    val rollingAmount: Long,
    val joinedAt: LocalDateTime,
    val termsAgreedAt: LocalDateTime?,
    val privacyAgreedAt: LocalDateTime?,
    val migrated: Boolean,
)

data class TierHistoryResult(
    val fromCode: String?,
    val toCode: String,
    val basisAmount: Long,
    val periodLabel: String,
    val changedAt: LocalDateTime,
    val reason: TierChangeReason,
) {
    companion object {
        fun from(h: TierHistory) = TierHistoryResult(h.fromCode, h.toCode, h.basisAmount, h.periodLabel, h.changedAt, h.reason)
    }
}

/** 목록 필드에 등급 이력을 더한다(JSON 에서는 한 단계로 펼친다). */
data class AdminCustomerDetailResult(
    @get:JsonUnwrapped
    val customer: AdminCustomerResult,
    val tierHistory: List<TierHistoryResult>,
)

data class CustomerLookupResult(
    val userId: String,
    val status: CustomerStatus,
    val tier: TierSummaryResult,
    val agreed: Boolean,
)

data class TierRunResult(
    val id: Long,
    val periodLabel: String,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
    val reason: TierRunReason,
    val customers: Int,
    val changed: Int,
    /** 등급 코드 → 고객 수. */
    val countsByTier: Map<String, Long>,
    val couponsIssued: Int,
    val couponsSkipped: Int,
    val status: TierRunStatus,
    val message: String?,
) {
    companion object {
        private val COUNT = Regex("\"([^\"]+)\":(\\d+)")

        fun from(r: TierRun) =
            TierRunResult(
                requireNotNull(r.id),
                r.periodLabel,
                r.startedAt,
                r.finishedAt,
                r.reason,
                r.customers,
                r.changed,
                r.countsByTier?.let { json -> COUNT.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toLong() } }.orEmpty(),
                r.couponsIssued,
                r.couponsSkipped,
                r.status,
                r.message,
            )
    }
}

/** 주문의 구매 적립(배송 완료 뒤 정해진다). */
data class OrderEarnResult(
    val status: EarnStatus,
    val points: Long,
    val rate: Int,
)

/** 결제완료·배송중 주문의 적립 예정(지금 등급 기준). */
data class ExpectedEarnResult(
    val points: Long,
    val rate: Int,
)

fun Customer.summaryTier(tiers: List<Tier>): Tier = tiers.firstOrNull { it.code == tierCode } ?: tiers.minBy { it.minAmount }
