package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.UserCoupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponRwRepository : RwRepository<Coupon, Long> {
    /** 발급할 때 수량을 세려고 쿠폰 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id and c.deletedAt is null")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Coupon?

    @Query("select c from Coupon c where c.code = :code and c.deletedAt is null")
    fun findLiveByCode(
        @Param("code") code: String,
    ): Coupon?

    @Query("select c from Coupon c where c.id = :id and c.deletedAt is null")
    fun findLive(
        @Param("id") id: Long,
    ): Coupon?

    @Query("select c from Coupon c where c.code = :code")
    fun findAnyByCode(
        @Param("code") code: String,
    ): Coupon?
}

interface UserCouponRwRepository : RwRepository<UserCoupon, Long> {
    fun existsByCouponIdAndUserId(
        couponId: Long,
        userId: String,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uc from UserCoupon uc join fetch uc.coupon where uc.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): UserCoupon?

    @Query("select uc from UserCoupon uc join fetch uc.coupon where uc.userId = :userId and uc.usedAt is null")
    fun findUnused(
        @Param("userId") userId: String,
    ): List<UserCoupon>
}
