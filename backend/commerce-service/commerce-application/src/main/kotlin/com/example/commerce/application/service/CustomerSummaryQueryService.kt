package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.OrderStatusStat
import com.example.commerce.application.domain.repository.ro.ReviewRoRepository
import com.example.commerce.application.domain.repository.ro.UserCouponCounts
import com.example.commerce.application.domain.repository.ro.UserCouponRoRepository
import com.example.commerce.application.domain.repository.ro.WishlistRoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** 백오피스 회원 요약용 집계. 전부 개수·합계 쿼리이고 주문은 최근 몇 건만 읽는다. */
@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class CustomerSummaryQueryService(
    private val orderRoRepository: OrderRoRepository,
    private val userCouponRoRepository: UserCouponRoRepository,
    private val wishlistRoRepository: WishlistRoRepository,
    private val reviewRoRepository: ReviewRoRepository,
    private val clock: Clock,
) {
    fun orderStats(userId: String): List<OrderStatusStat> = orderRoRepository.statsByStatus(userId)

    /** 최신 주문부터(앱 주문 목록과 같은 순서). */
    fun recentOrders(
        userId: String,
        limit: Int,
    ): List<Order> = orderRoRepository.findAllByUserIdOrderByIdDesc(userId, PageRequest.of(0, limit)).content

    /** 앱 내 쿠폰과 같은 오늘(LocalDate.now(clock)) 기준. */
    fun couponCounts(userId: String): UserCouponCounts = userCouponRoRepository.countByStatus(userId, LocalDate.now(clock))

    fun wishlistCount(userId: String): Long = wishlistRoRepository.countLiveByUserId(userId)

    fun reviewCount(userId: String): Long = reviewRoRepository.countByUserId(userId)
}
