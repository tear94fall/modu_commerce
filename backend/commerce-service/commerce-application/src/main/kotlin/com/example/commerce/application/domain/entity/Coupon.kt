package com.example.commerce.application.domain.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

enum class DiscountType { FIXED, PERCENT }

/** CATEGORY 는 하위 카테고리까지 포함한다. */
enum class CouponScope { ALL, CATEGORY, PRODUCT }

enum class CouponSource { DOWNLOAD, CODE, ADMIN, EVENT }

enum class UserCouponStatus { AVAILABLE, USED, EXPIRED }

/** 쿠폰 할인 계산에 쓰는 주문 한 줄(상품과 그 줄의 금액). */
data class CouponLine(
    val product: Product,
    val amount: Long,
)

/**
 * 쿠폰 정의(백오피스에서 만든다). 발급된 쿠폰은 [UserCoupon].
 *
 * 날짜는 KST 달력 날짜다. 발급 기간([issueStart]~[issueEnd])은 받기·코드·이벤트 발급에만 적용되고 관리자 지급은 무시한다.
 * 사용 기한은 [validUntil](고정 날짜) 또는 [validDays](받은 날부터 N일) 중 하나다.
 * 지워도(deleted_at) 이미 받은 쿠폰은 기한까지 쓸 수 있다 — 그래서 @SQLRestriction 을 걸지 않고 조회에서 거른다.
 */
@Entity
@Table(name = "coupons", indexes = [Index(name = "ix_coupons_code", columnList = "code", unique = true)])
class Coupon(
    name: String,
    discountType: DiscountType,
    discountValue: Long,
    issueStart: LocalDate,
    issueEnd: LocalDate,
) : BaseEntity() {
    @Column(name = "name", nullable = false, length = 40)
    var name: String = name
        protected set

    @Column(name = "description", length = 200)
    var description: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    var discountType: DiscountType = discountType
        protected set

    @Column(name = "discount_value", nullable = false)
    var discountValue: Long = discountValue
        protected set

    @Column(name = "max_discount")
    var maxDiscount: Long? = null
        protected set

    @Column(name = "min_order_amount", nullable = false)
    var minOrderAmount: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    var scope: CouponScope = CouponScope.ALL
        protected set

    /** 카테고리 또는 상품 id. 전체 쿠폰이면 비어 있다. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coupon_scope_targets", joinColumns = [JoinColumn(name = "coupon_id")])
    @Column(name = "target_id", nullable = false)
    var scopeIds: MutableSet<Long> = mutableSetOf()
        protected set

    @Column(name = "issue_start", nullable = false)
    var issueStart: LocalDate = issueStart
        protected set

    @Column(name = "issue_end", nullable = false)
    var issueEnd: LocalDate = issueEnd
        protected set

    @Column(name = "valid_until")
    var validUntil: LocalDate? = null
        protected set

    @Column(name = "valid_days")
    var validDays: Int? = null
        protected set

    /** null 이면 무제한. */
    @Column(name = "total_quantity")
    var totalQuantity: Long? = null
        protected set

    /** 발급된 수. 발급할 때 쿠폰 행을 잠그고 올린다. */
    @Column(name = "issued_count", nullable = false)
    var issuedCount: Long = 0
        protected set

    @Column(name = "code", length = 20)
    var code: String? = null
        protected set

    @Column(name = "downloadable", nullable = false)
    var downloadable: Boolean = false
        protected set

    @Column(name = "active", nullable = false)
    var active: Boolean = true
        protected set

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    fun update(
        name: String,
        description: String?,
        discountType: DiscountType,
        discountValue: Long,
        maxDiscount: Long?,
        minOrderAmount: Long,
        scope: CouponScope,
        scopeIds: Collection<Long>,
        issueStart: LocalDate,
        issueEnd: LocalDate,
        validUntil: LocalDate?,
        validDays: Int?,
        totalQuantity: Long?,
        code: String?,
        downloadable: Boolean,
        active: Boolean,
    ) {
        this.name = name
        this.description = description
        this.discountType = discountType
        this.discountValue = discountValue
        this.maxDiscount = if (discountType == DiscountType.PERCENT) maxDiscount else null
        this.minOrderAmount = minOrderAmount
        this.scope = scope
        this.scopeIds.clear()
        if (scope != CouponScope.ALL) this.scopeIds.addAll(scopeIds)
        this.issueStart = issueStart
        this.issueEnd = issueEnd
        this.validUntil = validUntil
        this.validDays = validDays
        this.totalQuantity = totalQuantity
        this.code = code
        this.downloadable = downloadable
        this.active = active
    }

    fun delete(now: LocalDateTime) {
        deletedAt = now
    }

    fun isDeleted(): Boolean = deletedAt != null

    fun inIssuePeriod(today: LocalDate): Boolean = !today.isBefore(issueStart) && !today.isAfter(issueEnd)

    fun soldOut(): Boolean = totalQuantity?.let { issuedCount >= it } ?: false

    /** 오늘 받으면 언제까지 쓸 수 있는가. */
    fun expiresOn(issuedOn: LocalDate): LocalDate = validUntil ?: issuedOn.plusDays((validDays ?: 1).toLong() - 1)

    fun issued() {
        issuedCount += 1
    }

    /** 이 상품에 쓸 수 있는가(범위만 본다). */
    fun covers(product: Product): Boolean =
        when (scope) {
            CouponScope.ALL -> true
            CouponScope.PRODUCT -> product.id in scopeIds
            CouponScope.CATEGORY -> {
                val c = product.category
                c != null && (c.id in scopeIds || c.parent?.id in scopeIds)
            }
        }

    /**
     * 할인액. 범위 안 줄의 합에 적용한다. 최소 주문 금액은 상품 합계 전체와 비교한다.
     * 쓸 수 없으면 0 과 이유.
     */
    fun discountFor(lines: List<CouponLine>): Pair<Long, String?> {
        val total = lines.sumOf { it.amount }
        if (total < minOrderAmount) return 0L to "%,d원 이상 주문 시 사용 가능".format(minOrderAmount)
        val eligible = lines.filter { covers(it.product) }.sumOf { it.amount }
        if (eligible <= 0) return 0L to "적용 가능한 상품이 없습니다"
        val discount =
            when (discountType) {
                DiscountType.FIXED -> minOf(discountValue, eligible)
                DiscountType.PERCENT -> (eligible * discountValue / 100).let { d -> maxDiscount?.let { minOf(d, it) } ?: d }
            }
        return discount to null
    }
}

/**
 * 받은 쿠폰. 쿠폰·사용자마다 하나(유니크). 주문에 쓰면 [orderId] 와 [usedAt] 이 채워지고, 주문을 취소하면 비워 돌려준다.
 * 상태는 저장하지 않고 오늘 날짜로 정한다(쓴 적 있으면 USED, 기한이 지났으면 EXPIRED).
 */
@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_coupons", columnNames = ["coupon_id", "user_id"])],
    indexes = [Index(name = "ix_user_coupons_user", columnList = "user_id")],
)
class UserCoupon(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    val coupon: Coupon,
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    val source: CouponSource,
    @Column(name = "expires_on", nullable = false)
    val expiresOn: LocalDate,
) : BaseEntity() {
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null
        protected set

    @Column(name = "order_id")
    var orderId: Long? = null
        protected set

    @Column(name = "order_no", length = 20)
    var orderNo: String? = null
        protected set

    fun statusOn(today: LocalDate): UserCouponStatus =
        when {
            usedAt != null -> UserCouponStatus.USED
            today.isAfter(expiresOn) -> UserCouponStatus.EXPIRED
            else -> UserCouponStatus.AVAILABLE
        }

    fun use(
        order: Order,
        now: LocalDateTime,
    ) {
        usedAt = now
        orderId = order.id
        orderNo = order.orderNo
    }

    /** 주문 취소. 기한이 지났으면 돌려줘도 만료로 보인다. */
    fun restore() {
        usedAt = null
        orderId = null
        orderNo = null
    }
}
