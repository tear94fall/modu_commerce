package com.example.commerce.application.domain.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

/** 기획전(연관 상품 모음)과 이벤트(지금은 출석 체크 하나). */
enum class PromotionType { EXHIBITION, EVENT }

/** 오늘(KST)과 기간으로 정해진다. 저장하지 않는다. */
enum class PromotionStatus { UPCOMING, ONGOING, ENDED }

/**
 * 기획전·이벤트. 앱 홈 상단 배너로 나가고 누르면 상세로 간다.
 *
 * 기간은 KST 달력 날짜(시작·종료 포함)다. 노출(visible)이 꺼지면 앱에서 통째로 사라진다. 삭제는 deleted_at(소프트 삭제).
 * 이벤트의 보상은 point-service 규칙([pointRuleCode])으로 적립하고, [rewardPoints] 는 화면 표시용 스냅숏이다.
 */
@Entity
@Table(name = "promotions", indexes = [Index(name = "ix_promotions_visible", columnList = "visible,start_date,end_date")])
@SQLRestriction("deleted_at IS NULL")
class Promotion(
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    val type: PromotionType,
    title: String,
    startDate: LocalDate,
    endDate: LocalDate,
) : BaseEntity() {
    @Column(name = "title", nullable = false, length = 60)
    var title: String = title
        protected set

    @Column(name = "subtitle", length = 100)
    var subtitle: String? = null
        protected set

    @Column(name = "description", length = 2000)
    var description: String? = null
        protected set

    @Column(name = "banner_image_url", length = 500)
    var bannerImageUrl: String? = null
        protected set

    @Column(name = "banner_color", length = 7)
    var bannerColor: String? = null
        protected set

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate = startDate
        protected set

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate = endDate
        protected set

    @Column(name = "visible", nullable = false)
    var visible: Boolean = false
        protected set

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
        protected set

    @Column(name = "point_rule_code", length = 64)
    var pointRuleCode: String? = null
        protected set

    @Column(name = "reward_points")
    var rewardPoints: Long? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    /** 기획전 상품. 관리자가 정한 순서(position)대로. */
    @OneToMany(mappedBy = "promotion", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    val products: MutableList<PromotionProduct> = mutableListOf()

    fun update(
        title: String,
        subtitle: String?,
        description: String?,
        bannerImageUrl: String?,
        bannerColor: String?,
        startDate: LocalDate,
        endDate: LocalDate,
        visible: Boolean,
        sortOrder: Int,
    ) {
        require(!endDate.isBefore(startDate)) { "종료일은 시작일보다 빠를 수 없습니다." }
        this.title = title
        this.subtitle = subtitle
        this.description = description
        this.bannerImageUrl = bannerImageUrl
        this.bannerColor = bannerColor
        this.startDate = startDate
        this.endDate = endDate
        this.visible = visible
        this.sortOrder = sortOrder
    }

    /** 기획전 상품을 통째로 바꾼다. 순서는 목록 순서다. */
    fun replaceProducts(productIds: List<Long>) {
        require(type == PromotionType.EXHIBITION) { "기획전에만 상품을 넣을 수 있습니다." }
        // 남는 상품은 기존 행의 순서만 바꾼다. 비우고 다시 넣으면 Hibernate 가 지우기 전에 넣어 유니크 제약에 걸린다.
        val existing = products.associateBy { it.productId }
        products.removeIf { it.productId !in productIds }
        productIds.forEachIndexed { i, id ->
            val row = existing[id]
            if (row != null) row.position = i else products.add(PromotionProduct(this, id, i))
        }
    }

    fun productIds(): List<Long> = products.sortedBy { it.position }.map { it.productId }

    fun setReward(
        pointRuleCode: String?,
        rewardPoints: Long?,
    ) {
        require(type == PromotionType.EVENT) { "이벤트에만 보상을 정할 수 있습니다." }
        this.pointRuleCode = pointRuleCode
        this.rewardPoints = if (pointRuleCode == null) null else rewardPoints
    }

    fun delete(now: LocalDateTime) {
        deletedAt = now
    }

    fun statusOn(today: LocalDate): PromotionStatus =
        when {
            today.isBefore(startDate) -> PromotionStatus.UPCOMING
            today.isAfter(endDate) -> PromotionStatus.ENDED
            else -> PromotionStatus.ONGOING
        }

    /** 기간 일수(시작·종료 포함). */
    fun totalDays(): Int = (endDate.toEpochDay() - startDate.toEpochDay() + 1).toInt()
}

@Entity
@Table(
    name = "promotion_products",
    uniqueConstraints = [UniqueConstraint(name = "uk_promotion_products", columnNames = ["promotion_id", "product_id"])],
)
class PromotionProduct(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    val promotion: Promotion,
    /**
     * 상품 id 만 들고 있다. 상품은 소프트 삭제에 @SQLRestriction 이 걸려 있어 연관으로 들면 지운 상품을 읽을 때 터진다.
     * 상품은 쓰는 쪽이 id 로 다시 조회한다(지운 상품은 자연히 빠진다).
     */
    @Column(name = "product_id", nullable = false)
    val productId: Long,
    position: Int,
) : IdentityEntity() {
    @Column(name = "position", nullable = false)
    var position: Int = position
        internal set
}

/**
 * 출석 체크 한 번. 이벤트·사용자·날짜(KST)마다 하나(유니크)라 동시에 두 번 눌러도 한 번만 남는다.
 * [rewardPoints] 는 실제로 적립된 포인트(규칙이 없거나 한도에 걸리면 0, 이유는 [rewardMessage]).
 */
@Entity
@Table(
    name = "attendance_checks",
    uniqueConstraints = [UniqueConstraint(name = "uk_attendance_checks", columnNames = ["promotion_id", "user_id", "check_date"])],
    indexes = [Index(name = "ix_attendance_checks_user", columnList = "promotion_id,user_id")],
)
class AttendanceCheck(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    val promotion: Promotion,
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "check_date", nullable = false)
    val checkDate: LocalDate,
) : BaseEntity() {
    @Column(name = "reward_points", nullable = false)
    var rewardPoints: Long = 0
        protected set

    @Column(name = "reward_message", length = 200)
    var rewardMessage: String? = null
        protected set

    fun rewarded(
        points: Long,
        message: String?,
    ) {
        rewardPoints = points
        rewardMessage = message
    }
}
