package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.UserCoupon
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

/** 쿠폰별 개수 한 줄. */
data class CouponCount(
    val couponId: Long,
    val count: Long,
)

/** 회원 한 명의 받은 쿠폰 상태별 개수(오늘 기준). */
data class UserCouponCounts(
    val available: Long?,
    val used: Long?,
    val expired: Long?,
)

interface CouponRoRepository : RoRepository<Coupon, Long> {
    @Query("select c from Coupon c where c.id = :id and c.deletedAt is null")
    fun findLive(
        @Param("id") id: Long,
    ): Coupon?

    @Query("select c from Coupon c where c.id in :ids and c.deletedAt is null")
    fun findLiveByIds(
        @Param("ids") ids: Collection<Long>,
    ): List<Coupon>

    /** 앱에서 받을 수 있는 쿠폰: 활성, 받기 노출, 오늘이 발급 기간 안. 최신 순. */
    @Query(
        "select c from Coupon c where c.deletedAt is null and c.active = true and c.downloadable = true " +
            "and c.issueStart <= :today and c.issueEnd >= :today order by c.id desc",
    )
    fun findDownloadable(
        @Param("today") today: LocalDate,
    ): List<Coupon>

    @Query(
        "select c from Coupon c where c.deletedAt is null and (:active is null or c.active = :active) " +
            "and (:q is null or lower(c.name) like lower(concat('%', :q, '%')) or lower(c.code) like lower(concat('%', :q, '%'))) " +
            "order by c.id desc",
    )
    fun searchAdmin(
        @Param("q") q: String?,
        @Param("active") active: Boolean?,
        pageable: Pageable,
    ): Page<Coupon>
}

interface UserCouponRoRepository : RoRepository<UserCoupon, Long> {
    @Query("select uc from UserCoupon uc join fetch uc.coupon where uc.userId = :userId")
    fun findAllByUser(
        @Param("userId") userId: String,
    ): List<UserCoupon>

    @Query("select uc.coupon.id from UserCoupon uc where uc.userId = :userId and uc.coupon.id in :couponIds and uc.issueKey = 'once'")
    fun findOwnedCouponIds(
        @Param("userId") userId: String,
        @Param("couponIds") couponIds: Collection<Long>,
    ): List<Long>

    @Query(
        "select new com.example.commerce.application.domain.repository.ro.CouponCount(uc.coupon.id, count(uc)) " +
            "from UserCoupon uc where uc.coupon.id in :ids and uc.usedAt is not null group by uc.coupon.id",
    )
    fun countUsed(
        @Param("ids") ids: Collection<Long>,
    ): List<CouponCount>

    /** 백오피스 발급 현황. status 가 null 이면 전부, 아니면 AVAILABLE / USED / EXPIRED(오늘 기준). 최신 순. */
    @Query(
        "select uc from UserCoupon uc where uc.coupon.id = :couponId and (:status is null " +
            "or (:status = 'USED' and uc.usedAt is not null) " +
            "or (:status = 'AVAILABLE' and uc.usedAt is null and uc.expiresOn >= :today) " +
            "or (:status = 'EXPIRED' and uc.usedAt is null and uc.expiresOn < :today)) order by uc.id desc",
    )
    fun findIssues(
        @Param("couponId") couponId: Long,
        @Param("status") status: String?,
        @Param("today") today: LocalDate,
        pageable: Pageable,
    ): Page<UserCoupon>

    /** 받은 쿠폰 상태별 개수. [UserCoupon.statusOn] 과 같은 규칙(쓴 적 있으면 USED, 기한이 오늘보다 앞이면 EXPIRED). 없으면 null 합. */
    @Query(
        "select new com.example.commerce.application.domain.repository.ro.UserCouponCounts(" +
            "sum(case when uc.usedAt is null and uc.expiresOn >= :today then 1L else 0L end), " +
            "sum(case when uc.usedAt is not null then 1L else 0L end), " +
            "sum(case when uc.usedAt is null and uc.expiresOn < :today then 1L else 0L end)) " +
            "from UserCoupon uc where uc.userId = :userId",
    )
    fun countByStatus(
        @Param("userId") userId: String,
        @Param("today") today: LocalDate,
    ): UserCouponCounts

    /** 쿠폰 하나의 받은 장 수를 상태별로(오늘 기준) — 백오피스 성과. */
    @Query(
        "select new com.example.commerce.application.domain.repository.ro.UserCouponCounts(" +
            "sum(case when uc.usedAt is null and uc.expiresOn >= :today then 1L else 0L end), " +
            "sum(case when uc.usedAt is not null then 1L else 0L end), " +
            "sum(case when uc.usedAt is null and uc.expiresOn < :today then 1L else 0L end)) " +
            "from UserCoupon uc where uc.coupon.id = :couponId",
    )
    fun countsForCoupon(
        @Param("couponId") couponId: Long,
        @Param("today") today: LocalDate,
    ): UserCouponCounts

    /** 발급 경로별 장 수. 각 행은 [source, count]. */
    @Query("select uc.source, count(uc) from UserCoupon uc where uc.coupon.id = :couponId group by uc.source")
    fun countBySource(
        @Param("couponId") couponId: Long,
    ): List<Array<Any>>

    /** 쓴 시각만(UTC). [from, to). 백오피스 성과의 사용 추이. */
    @Query(
        "select uc.usedAt from UserCoupon uc where uc.coupon.id = :couponId and uc.usedAt >= :from and uc.usedAt < :to",
    )
    fun findUsedAtBetween(
        @Param("couponId") couponId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<LocalDateTime>

    /** 쿠폰 받기 이벤트로 나간 장 수와 그중 쓴 장 수(쿠폰별, source 로 거른다). 각 행은 [couponId, issued, used]. */
    @Query(
        "select uc.coupon.id, count(uc), sum(case when uc.usedAt is not null then 1L else 0L end) " +
            "from UserCoupon uc where uc.coupon.id in :couponIds and uc.source = :source group by uc.coupon.id",
    )
    fun countEventIssues(
        @Param("couponIds") couponIds: Collection<Long>,
        @Param("source") source: CouponSource,
    ): List<Array<Any>>
}
