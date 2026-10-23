package com.example.commerce.application.usecase.stats

import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.Promotion
import com.example.commerce.application.domain.entity.PromotionProduct
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.domain.repository.ro.AttendanceCheckRoRepository
import com.example.commerce.application.domain.repository.ro.CouponRoRepository
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.PromotionRoRepository
import com.example.commerce.application.domain.repository.ro.UserCouponRoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

class GetPromotionPerformanceUseCaseTest {
    private val promotions = mock<PromotionRoRepository>()
    private val orders = mock<OrderRoRepository>()
    private val attendances = mock<AttendanceCheckRoRepository>()
    private val coupons = mock<CouponRoRepository>()
    private val userCoupons = mock<UserCouponRoRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-02T15:30:00Z"), ZoneId.of("Asia/Seoul")) // 10-03 00:30 KST
    private val useCase = GetPromotionPerformanceUseCase(promotions, orders, attendances, coupons, userCoupons, clock)

    @Test
    @DisplayName("기획전: 한국 날짜 기간을 UTC 로 바꿔 조회하고, 취소 주문은 통째로 빼며, 상품별 매출 순으로 낸다")
    fun exhibition() {
        val p =
            Promotion(
                type = PromotionType.EXHIBITION,
                title = "가을",
                startDate = LocalDate.parse("2026-10-20"),
                endDate = LocalDate.parse("2026-10-22"),
            )
        p.products.add(PromotionProduct(p, 101L, 0))
        p.products.add(PromotionProduct(p, 102L, 1))
        whenever(promotions.findById(5L)).thenReturn(p)
        whenever(orders.findPromotionItemRows(any(), any(), any())).thenReturn(
            listOf(
                arrayOf(1L, OrderStatus.PAID, 101L, "머그", 2, 5_000L),
                arrayOf(1L, OrderStatus.PAID, 102L, "노트", 1, 3_000L),
                arrayOf(2L, OrderStatus.DELIVERED, 102L, "노트", 3, 3_000L),
                arrayOf(3L, OrderStatus.CANCELLED, 101L, "머그", 9, 5_000L),
            ),
        )

        val r = useCase.execute(5L)
        val e = requireNotNull(r.exhibition)

        verify(
            orders,
        ).findPromotionItemRows(
            eq(listOf(101L, 102L)),
            eq(LocalDateTime.parse("2026-10-19T15:00:00")),
            eq(LocalDateTime.parse("2026-10-22T15:00:00")),
        )
        assertEquals(2L, e.orders)
        assertEquals(1L, e.cancelledOrders)
        assertEquals(6L, e.units)
        assertEquals(22_000L, e.sales)
        assertEquals(listOf("노트" to 12_000L, "머그" to 10_000L), e.products.map { it.name to it.sales })
        assertNull(r.attendance)
        assertNull(r.coupons)
    }

    @Test
    @DisplayName("출석 이벤트: 참여자는 서로 다른 회원 수, 날짜별 출석은 시작일부터 오늘(KST)까지 빠짐없이 0 포함")
    fun attendance() {
        val p =
            Promotion(
                type = PromotionType.EVENT,
                title = "출석",
                startDate = LocalDate.parse("2026-10-01"),
                endDate = LocalDate.parse("2026-10-31"),
            )
        whenever(promotions.findById(6L)).thenReturn(p)
        whenever(attendances.findRows(6L)).thenReturn(
            listOf(
                arrayOf("u1", LocalDate.parse("2026-10-02"), 10L),
                arrayOf("u1", LocalDate.parse("2026-10-01"), 10L),
                arrayOf("u2", LocalDate.parse("2026-10-02"), 50L),
            ),
        )

        val a = requireNotNull(useCase.execute(6L).attendance)
        assertEquals(2L, a.participants)
        assertEquals(3L, a.checks)
        assertEquals(70L, a.rewardPoints)
        assertEquals(
            listOf(LocalDate.parse("2026-10-01") to 1L, LocalDate.parse("2026-10-02") to 2L, LocalDate.parse("2026-10-03") to 0L),
            a.byDay.map { it.date to it.count },
        )
    }
}
