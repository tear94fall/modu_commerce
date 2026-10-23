package com.example.commerce.application.usecase.stats

import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.repository.ro.CouponRoRepository
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.UserCouponRoRepository
import com.example.commerce.application.service.DayWindow
import com.example.commerce.application.usecase.result.CouponPerformanceResult
import com.example.commerce.application.usecase.result.CouponSourceCount
import com.example.commerce.application.usecase.result.DayCount
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.ZoneId

/** 백오피스 쿠폰 상세의 성과. 장 수는 user_coupon, 돈은 그 쿠폰을 쓴 주문에서. 전부 replica. */
@Component
class GetCouponPerformanceUseCase(
    private val coupons: CouponRoRepository,
    private val userCoupons: UserCouponRoRepository,
    private val orders: OrderRoRepository,
    private val clock: Clock,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        couponId: Long,
        days: Int,
        zone: ZoneId,
    ): CouponPerformanceResult {
        coupons.findLive(couponId) ?: throw EntityNotFoundException("쿠폰이 없습니다: $couponId")
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val counts = userCoupons.countsForCoupon(couponId, today)
        val available = counts.available ?: 0L
        val used = counts.used ?: 0L
        val expired = counts.expired ?: 0L
        val issued = available + used + expired
        val bySource =
            userCoupons
                .countBySource(couponId)
                .map { CouponSourceCount(it[0] as CouponSource, (it[1] as Number).toLong()) }
                .sortedByDescending { it.count }

        val rows = orders.findCouponOrderRows(couponId).map { OrderRow.of(it) }
        val live = rows.filter { it.status != OrderStatus.CANCELLED }
        val revenue = live.sumOf { it.totalAmount - it.couponDiscount - it.pointAmount }

        val window = DayWindow(zone, days, clock)
        val usedAt = userCoupons.findUsedAtBetween(couponId, window.fromStored, window.toStored)
        val byDay = usedAt.groupingBy { window.dateOf(it) }.eachCount()

        return CouponPerformanceResult(
            couponId = couponId,
            issued = issued,
            used = used,
            available = available,
            expired = expired,
            usageRate = if (issued > 0) (used * 100 / issued).toInt() else null,
            bySource = bySource,
            orders = live.size.toLong(),
            cancelledOrders = (rows.size - live.size).toLong(),
            discountTotal = live.sumOf { it.couponDiscount },
            revenue = revenue,
            avgOrderAmount = if (live.isNotEmpty()) revenue / live.size else null,
            zone = zone.id,
            days = window.dates.size,
            usedByDay = window.dates.map { DayCount(it, (byDay[it] ?: 0).toLong()) },
        )
    }

    private data class OrderRow(
        val status: OrderStatus,
        val totalAmount: Long,
        val couponDiscount: Long,
        val pointAmount: Long,
    ) {
        companion object {
            fun of(r: Array<Any?>) =
                OrderRow(r[0] as OrderStatus, (r[1] as Number).toLong(), (r[2] as Number).toLong(), (r[3] as Number).toLong())
        }
    }
}
