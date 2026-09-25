package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.UserCoupon
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/** 쿠폰별 개수 한 줄. */
data class CouponCount(
    val couponId: Long,
    val count: Long,
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

    @Query("select uc.coupon.id from UserCoupon uc where uc.userId = :userId and uc.coupon.id in :couponIds")
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
}
