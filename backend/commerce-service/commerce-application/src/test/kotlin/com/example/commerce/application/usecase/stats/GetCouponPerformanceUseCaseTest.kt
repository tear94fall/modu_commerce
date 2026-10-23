package com.example.commerce.application.usecase.stats

import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.repository.ro.CouponRoRepository
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.UserCouponCounts
import com.example.commerce.application.domain.repository.ro.UserCouponRoRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GetCouponPerformanceUseCaseTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val coupons = mock<CouponRoRepository>()
    private val userCoupons = mock<UserCouponRoRepository>()
    private val orders = mock<OrderRoRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T15:30:00Z"), seoul) // 10-23 00:30 KST
    private val useCase = GetCouponPerformanceUseCase(coupons, userCoupons, orders, clock)

    @Test
    @DisplayName("장 수는 user_coupon 에서, 돈은 쿠폰을 쓴 주문에서 — 취소 주문은 할인·매출에서 빠진다")
    fun aggregatesCountsAndMoney() {
        whenever(coupons.findLive(7L)).thenReturn(mock<Coupon>())
        whenever(userCoupons.countsForCoupon(eq(7L), eq(LocalDate.parse("2026-10-23")))).thenReturn(UserCouponCounts(5, 3, 2))
        whenever(userCoupons.countBySource(7L)).thenReturn(listOf(arrayOf(CouponSource.DOWNLOAD, 8L), arrayOf(CouponSource.ADMIN, 2L)))
        whenever(orders.findCouponOrderRows(7L)).thenReturn(
            listOf(
                arrayOf(OrderStatus.PAID, 30_000L, 3_000L, 1_000L),
                arrayOf(OrderStatus.DELIVERED, 20_000L, 2_000L, 0L),
                arrayOf(OrderStatus.CANCELLED, 50_000L, 5_000L, 0L),
            ),
        )
        whenever(userCoupons.findUsedAtBetween(eq(7L), any(), any())).thenReturn(
            listOf(LocalDateTime.parse("2026-10-22T15:10:00"), LocalDateTime.parse("2026-10-22T14:00:00")), // 10-23, 10-22 KST
        )

        val r = useCase.execute(7L, 2, seoul)

        assertEquals(10L, r.issued)
        assertEquals(3L, r.used)
        assertEquals(30, r.usageRate)
        assertEquals(listOf(CouponSource.DOWNLOAD to 8L, CouponSource.ADMIN to 2L), r.bySource.map { it.source to it.count })
        assertEquals(2L, r.orders)
        assertEquals(1L, r.cancelledOrders)
        assertEquals(5_000L, r.discountTotal)
        assertEquals(44_000L, r.revenue) // 26,000 + 18,000
        assertEquals(22_000L, r.avgOrderAmount)
        assertEquals(listOf(1L, 1L), r.usedByDay.map { it.count })
        assertEquals(listOf(LocalDate.parse("2026-10-22"), LocalDate.parse("2026-10-23")), r.usedByDay.map { it.date })
    }

    @Test
    @DisplayName("발급이 없으면 사용률·평균은 null, 없는 쿠폰은 404 예외")
    fun emptyAndMissing() {
        whenever(coupons.findLive(1L)).thenReturn(mock<Coupon>())
        whenever(userCoupons.countsForCoupon(eq(1L), any())).thenReturn(UserCouponCounts(null, null, null))
        whenever(userCoupons.countBySource(1L)).thenReturn(emptyList())
        whenever(orders.findCouponOrderRows(1L)).thenReturn(emptyList())
        whenever(userCoupons.findUsedAtBetween(eq(1L), any(), any())).thenReturn(emptyList())

        val r = useCase.execute(1L, 14, seoul)
        assertEquals(0L, r.issued)
        assertNull(r.usageRate)
        assertNull(r.avgOrderAmount)
        assertEquals(14, r.usedByDay.size)

        whenever(coupons.findLive(9L)).thenReturn(null)
        assertThrows(EntityNotFoundException::class.java) { useCase.execute(9L, 7, seoul) }
    }
}
