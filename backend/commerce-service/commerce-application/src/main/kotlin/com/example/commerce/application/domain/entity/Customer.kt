package com.example.commerce.application.domain.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class CustomerStatus { ACTIVE, WITHDRAWN }

/**
 * 커머스 고객. 약관에 동의해 가입한 모두 계정 회원이다(모두 챗만 쓰는 회원과 구별한다).
 * 기존 데이터에서 옮겨 온 행은 [migrated] = true 이고 동의 시각이 비어 있다(앱에서 동의하면 채워진다).
 * 시각은 모두 UTC 다.
 */
@Entity
@Table(
    name = "commerce_customers",
    indexes = [
        Index(name = "ix_commerce_customers_tier", columnList = "tier_code"),
        Index(name = "ix_commerce_customers_joined", columnList = "joined_at"),
    ],
)
class Customer(
    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    joinedAt: LocalDateTime,
    tierCode: String,
    migrated: Boolean = false,
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: CustomerStatus = CustomerStatus.ACTIVE
        protected set

    @Column(name = "joined_at", nullable = false)
    var joinedAt: LocalDateTime = joinedAt
        protected set

    @Column(name = "terms_agreed_at")
    var termsAgreedAt: LocalDateTime? = null
        protected set

    @Column(name = "privacy_agreed_at")
    var privacyAgreedAt: LocalDateTime? = null
        protected set

    @Column(name = "terms_version", length = 20)
    var termsVersion: String? = null
        protected set

    @Column(name = "tier_code", nullable = false, length = 20)
    var tierCode: String = tierCode
        protected set

    @Column(name = "tier_since", nullable = false)
    var tierSince: LocalDateTime = joinedAt
        protected set

    /** 지금 등급을 정한 금액(지난 6개월 배송 완료 결제 금액). */
    @Column(name = "tier_basis_amount", nullable = false)
    var tierBasisAmount: Long = 0
        protected set

    @Column(name = "withdrawn_at")
    var withdrawnAt: LocalDateTime? = null
        protected set

    @Column(name = "migrated", nullable = false)
    var migrated: Boolean = migrated
        protected set

    /** 앱에서 쓸 수 있는 고객인가(가입 상태이고 필수 약관 둘 다 동의). */
    fun isAgreed(): Boolean = status == CustomerStatus.ACTIVE && termsAgreedAt != null && privacyAgreedAt != null

    fun isActive(): Boolean = status == CustomerStatus.ACTIVE

    /** 가입(동의). 옮겨 온 행·탈퇴한 행도 데이터는 그대로 두고 동의와 상태만 바꾼다. */
    fun agree(
        termsVersion: String,
        now: LocalDateTime,
    ) {
        termsAgreedAt = now
        privacyAgreedAt = now
        this.termsVersion = termsVersion
        status = CustomerStatus.ACTIVE
        withdrawnAt = null
    }

    fun withdraw(now: LocalDateTime) {
        if (status == CustomerStatus.WITHDRAWN) return
        status = CustomerStatus.WITHDRAWN
        withdrawnAt = now
    }

    /** 등급 산정 결과. 등급이 바뀌었으면 true(이때만 [tierSince] 가 바뀐다). */
    fun applyTier(
        code: String,
        basis: Long,
        now: LocalDateTime,
    ): Boolean {
        tierBasisAmount = basis
        if (code == tierCode) return false
        tierCode = code
        tierSince = now
        return true
    }
}

/** 회원 등급 정의. 코드는 고정(WELCOME/SILVER/GOLD/VIP)이고 이름·색·기준 금액·적립률·매월 쿠폰을 바꿀 수 있다. */
@Entity
@Table(name = "commerce_tiers")
class Tier(
    @Id
    @Column(name = "code", nullable = false, length = 20)
    val code: String,
    name: String,
    color: String,
    minAmount: Long,
    earnRate: Int,
    sortOrder: Int,
) {
    @Column(name = "name", nullable = false, length = 20)
    var name: String = name
        protected set

    /** #RRGGBB. */
    @Column(name = "color", nullable = false, length = 7)
    var color: String = color
        protected set

    @Column(name = "min_amount", nullable = false)
    var minAmount: Long = minAmount
        protected set

    /** 구매 적립률(%). 0..20. */
    @Column(name = "earn_rate", nullable = false)
    var earnRate: Int = earnRate
        protected set

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = sortOrder
        protected set

    /** 매월 1일 이 등급 고객에게 주는 쿠폰(coupons.id). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "commerce_tier_coupons", joinColumns = [JoinColumn(name = "tier_code")])
    @OrderColumn(name = "position")
    @Column(name = "coupon_id", nullable = false)
    var couponIds: MutableList<Long> = mutableListOf()
        protected set

    fun update(
        name: String,
        color: String,
        minAmount: Long,
        earnRate: Int,
        sortOrder: Int,
        couponIds: List<Long>,
    ) {
        this.name = name
        this.color = color
        this.minAmount = minAmount
        this.earnRate = earnRate
        this.sortOrder = sortOrder
        this.couponIds.clear()
        this.couponIds.addAll(couponIds)
    }

    /** 결제 금액에 대한 적립 포인트(내림). */
    fun earnFor(paymentAmount: Long): Long = if (paymentAmount <= 0) 0 else paymentAmount * earnRate / 100
}

enum class TierChangeReason { MONTHLY, MANUAL, JOIN }

enum class TierRunReason { MONTHLY, MANUAL }

enum class TierRunStatus { RUNNING, DONE, FAILED }

/** 등급이 바뀐 기록. [periodLabel] 은 기준 기간("2026.04 ~ 2026.09"). */
@Entity
@Table(name = "commerce_tier_histories", indexes = [Index(name = "ix_tier_histories_user", columnList = "user_id,changed_at")])
class TierHistory(
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "from_code", length = 20)
    val fromCode: String?,
    @Column(name = "to_code", nullable = false, length = 20)
    val toCode: String,
    @Column(name = "basis_amount", nullable = false)
    val basisAmount: Long,
    @Column(name = "period_label", nullable = false, length = 30)
    val periodLabel: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 10)
    val reason: TierChangeReason,
    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime,
    @Column(name = "run_id")
    val runId: Long? = null,
) : IdentityEntity()

/** 등급 산정 실행 한 번(매월 자동 또는 관리자 수동). 동시에 하나만 RUNNING 이다. */
@Entity
@Table(name = "commerce_tier_runs", indexes = [Index(name = "ix_tier_runs_status", columnList = "status")])
class TierRun(
    @Column(name = "period_label", nullable = false, length = 30)
    val periodLabel: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 10)
    val reason: TierRunReason,
    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime,
) : IdentityEntity() {
    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null
        protected set

    @Column(name = "customers", nullable = false)
    var customers: Int = 0
        protected set

    @Column(name = "changed", nullable = false)
    var changed: Int = 0
        protected set

    /** 등급별 고객 수 JSON(`{"WELCOME":3,"SILVER":1}`). */
    @Column(name = "counts_by_tier", length = 500)
    var countsByTier: String? = null
        protected set

    @Column(name = "coupons_issued", nullable = false)
    var couponsIssued: Int = 0
        protected set

    @Column(name = "coupons_skipped", nullable = false)
    var couponsSkipped: Int = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: TierRunStatus = TierRunStatus.RUNNING
        protected set

    @Column(name = "message", length = 500)
    var message: String? = null
        protected set

    fun recalculated(
        customers: Int,
        changed: Int,
        countsByTier: String,
    ) {
        this.customers = customers
        this.changed = changed
        this.countsByTier = countsByTier
    }

    fun done(
        issued: Int,
        skipped: Int,
        now: LocalDateTime,
    ) {
        couponsIssued = issued
        couponsSkipped = skipped
        status = TierRunStatus.DONE
        finishedAt = now
    }

    fun failed(
        message: String,
        now: LocalDateTime,
    ) {
        this.message = message.take(500)
        status = TierRunStatus.FAILED
        finishedAt = now
    }
}
