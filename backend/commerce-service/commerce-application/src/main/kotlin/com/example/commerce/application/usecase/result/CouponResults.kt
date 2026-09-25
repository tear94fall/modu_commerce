package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.CouponScope
import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.DiscountType
import com.example.commerce.application.domain.entity.UserCoupon
import com.example.commerce.application.domain.entity.UserCouponStatus
import com.example.commerce.application.service.CouponQueryService
import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDate
import java.time.LocalDateTime

/** 받을 수 있는 쿠폰 한 장(쿠폰존, 상품 상세, 기획전·쿠폰 이벤트). */
data class CouponOfferResult(
    val couponId: Long,
    val name: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long,
    val scopeLabel: String,
    val expiryLabel: String,
    val downloaded: Boolean,
    val soldOut: Boolean,
) {
    companion object {
        fun from(
            c: Coupon,
            scopeLabel: String,
            downloaded: Boolean,
        ) = CouponOfferResult(
            requireNotNull(c.id),
            c.name,
            c.description,
            c.discountType,
            c.discountValue,
            c.maxDiscount,
            c.minOrderAmount,
            scopeLabel,
            CouponQueryService.expiryLabel(c),
            downloaded,
            c.soldOut(),
        )
    }
}

/** 내 쿠폰함 한 장. 결제 화면에서는 [discount]·[applicable]·[reason] 이 붙는다. */
data class MyCouponResult(
    val id: Long,
    val couponId: Long,
    val name: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long,
    val scopeLabel: String,
    val expiresOn: LocalDate,
    val status: UserCouponStatus,
    val source: CouponSource,
    val issuedAt: LocalDateTime?,
    val usedAt: LocalDateTime?,
    val orderId: Long?,
    val discount: Long? = null,
    val applicable: Boolean? = null,
    val reason: String? = null,
) {
    companion object {
        fun from(
            uc: UserCoupon,
            today: LocalDate,
            scopeLabel: String,
        ): MyCouponResult {
            val c = uc.coupon
            return MyCouponResult(
                requireNotNull(uc.id),
                requireNotNull(c.id),
                c.name,
                c.description,
                c.discountType,
                c.discountValue,
                c.maxDiscount,
                c.minOrderAmount,
                scopeLabel,
                uc.expiresOn,
                uc.statusOn(today),
                uc.source,
                uc.createdAt,
                uc.usedAt,
                uc.orderId,
            )
        }
    }
}

data class CouponClaimResult(
    val issued: List<MyCouponResult>,
    val alreadyHad: Int,
)

data class AdminCouponSummaryResult(
    val id: Long,
    val name: String,
    val code: String?,
    val discountType: DiscountType,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long,
    val scope: CouponScope,
    val scopeLabel: String,
    val issueStart: LocalDate,
    val issueEnd: LocalDate,
    val validUntil: LocalDate?,
    val validDays: Int?,
    val totalQuantity: Long?,
    val issuedCount: Long,
    val usedCount: Long,
    val downloadable: Boolean,
    val active: Boolean,
    val createdAt: LocalDateTime?,
) {
    companion object {
        fun from(
            c: Coupon,
            scopeLabel: String,
            usedCount: Long,
        ) = AdminCouponSummaryResult(
            requireNotNull(c.id),
            c.name,
            c.code,
            c.discountType,
            c.discountValue,
            c.maxDiscount,
            c.minOrderAmount,
            c.scope,
            scopeLabel,
            c.issueStart,
            c.issueEnd,
            c.validUntil,
            c.validDays,
            c.totalQuantity,
            c.issuedCount,
            usedCount,
            c.downloadable,
            c.active,
            c.createdAt,
        )
    }
}

data class ScopeTargetResult(
    val id: Long,
    val name: String,
)

/** 목록 필드에 설명·적용 대상을 더한다(JSON 에서는 한 단계로 펼친다). */
data class AdminCouponDetailResult(
    @get:JsonUnwrapped
    val summary: AdminCouponSummaryResult,
    val description: String?,
    val scopeIds: List<Long>,
    val scopeTargets: List<ScopeTargetResult>,
)

data class AdminCouponIssueResult(
    val id: Long,
    val userId: String,
    val source: CouponSource,
    val status: UserCouponStatus,
    val issuedAt: LocalDateTime?,
    val expiresOn: LocalDate,
    val usedAt: LocalDateTime?,
    val orderId: Long?,
    val orderNo: String?,
) {
    companion object {
        fun from(
            uc: UserCoupon,
            today: LocalDate,
        ) = AdminCouponIssueResult(
            requireNotNull(uc.id),
            uc.userId,
            uc.source,
            uc.statusOn(today),
            uc.createdAt,
            uc.expiresOn,
            uc.usedAt,
            uc.orderId,
            uc.orderNo,
        )
    }
}

data class SkippedUserResult(
    val userId: String,
    val reason: String,
)

data class CouponGrantResult(
    val issued: Int,
    val skipped: List<SkippedUserResult>,
)
