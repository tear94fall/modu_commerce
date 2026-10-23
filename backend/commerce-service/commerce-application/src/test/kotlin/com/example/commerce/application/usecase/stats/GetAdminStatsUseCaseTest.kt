package com.example.commerce.application.usecase.stats

import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GetAdminStatsUseCaseTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val orders = mock<OrderRoRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T15:30:00Z"), seoul) // 10-23 00:30 KST
    private val useCase = GetAdminStatsUseCase(orders, clock)

    private fun row(
        paidAt: String,
        status: OrderStatus = OrderStatus.PAID,
        total: Long = 10_000,
        coupon: Long = 0,
        point: Long = 0,
        userCouponId: Long? = null,
        cancelledAt: String? = null,
    ): Array<Any?> =
        arrayOf(LocalDateTime.parse(paidAt), status, total, coupon, point, userCouponId, cancelledAt?.let { LocalDateTime.parse(it) })

    @Test
    @DisplayName("결제는 결제한 날, 취소는 취소한 날에 세고 실결제액은 상품 − 쿠폰 − 포인트다")
    fun bucketsPaidAndCancelledByDisplayDate() {
        whenever(orders.findStatsRowsBetween(any(), any())).thenReturn(
            listOf(
                row("2026-10-22T15:05:00", total = 20_000, coupon = 3_000, point = 2_000, userCouponId = 7L), // 10-23 KST, 쿠폰 사용
                row("2026-10-22T14:00:00"), // 10-22 KST
                row("2026-10-21T20:00:00", status = OrderStatus.CANCELLED, cancelledAt = "2026-10-22T15:10:00"), // 10-22 결제 → 10-23 취소
            ),
        )

        val result = useCase.execute(2, seoul)

        assertEquals(listOf(LocalDate.parse("2026-10-22"), LocalDate.parse("2026-10-23")), result.days.map { it.date })
        assertEquals(listOf(2, 1), result.days.map { it.orders })
        assertEquals(listOf(20_000L, 15_000L), result.days.map { it.amount })
        assertEquals(listOf(0, 1), result.days.map { it.couponUsed })
        assertEquals(listOf(0, 1), result.days.map { it.cancelled })
        verify(orders).findStatsRowsBetween(eq(LocalDateTime.parse("2026-10-21T15:00:00")), eq(LocalDateTime.parse("2026-10-23T15:00:00")))
    }
}
