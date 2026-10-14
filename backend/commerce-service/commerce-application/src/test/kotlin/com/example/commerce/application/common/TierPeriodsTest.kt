package com.example.commerce.application.common

import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.service.above
import com.example.commerce.application.service.tierFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class TierPeriodsTest {
    private val tiers =
        listOf(
            Tier("VIP", "VIP", "#7C3AED", 700_000, 5, 3),
            Tier("WELCOME", "웰컴", "#64748B", 0, 1, 0),
            Tier("GOLD", "골드", "#D97706", 300_000, 3, 2),
            Tier("SILVER", "실버", "#94A3B8", 100_000, 2, 1),
        )

    @Test
    fun `기준 기간은 KST 달의 경계를 UTC 로 바꾼 반열린 구간이다`() {
        val oct = YearMonth.of(2026, 10)
        assertEquals(LocalDateTime.of(2026, 3, 31, 15, 0), TierPeriods.basisFrom(oct))
        assertEquals(LocalDateTime.of(2026, 9, 30, 15, 0), TierPeriods.basisTo(oct))
        assertEquals(LocalDateTime.of(2026, 4, 30, 15, 0), TierPeriods.rollingFrom(oct))
        assertEquals(LocalDateTime.of(2026, 10, 31, 15, 0), TierPeriods.rollingTo(oct))
        assertEquals("2026.04 ~ 2026.09", TierPeriods.basisLabel(oct))
        assertEquals("2025.07 ~ 2025.12", TierPeriods.basisLabel(YearMonth.of(2026, 1)))
        assertEquals("2026-10", TierPeriods.key(oct))
    }

    @Test
    fun `이번 달은 한국 시각으로 센다`() {
        // 9/30 15:30 UTC = 10/1 00:30 KST
        val clock = Clock.fixed(Instant.parse("2026-09-30T15:30:00Z"), ZoneOffset.UTC)
        assertEquals(YearMonth.of(2026, 10), TierPeriods.currentMonth(clock))
        assertEquals(LocalDateTime.of(2026, 9, 30, 15, 30), TierPeriods.utcNow(clock))
    }

    @Test
    fun `등급은 기준 금액 이상인 가장 높은 등급이고 경계 금액은 위 등급이다`() {
        assertEquals("WELCOME", tiers.tierFor(0).code)
        assertEquals("WELCOME", tiers.tierFor(99_999).code)
        assertEquals("SILVER", tiers.tierFor(100_000).code)
        assertEquals("SILVER", tiers.tierFor(299_999).code)
        assertEquals("GOLD", tiers.tierFor(300_000).code)
        assertEquals("VIP", tiers.tierFor(700_000).code)
        assertEquals("VIP", tiers.tierFor(10_000_000).code)
        assertEquals("GOLD", tiers.above(tiers.tierFor(100_000))?.code)
        assertNull(tiers.above(tiers.tierFor(700_000)))
    }

    @Test
    fun `적립은 결제 금액 × 적립률 ÷ 100 을 내림한다`() {
        val gold = tiers.first { it.code == "GOLD" }
        assertEquals(539, gold.earnFor(17_999))
        assertEquals(540, gold.earnFor(18_000))
        assertEquals(0, gold.earnFor(33))
        assertEquals(0, gold.earnFor(0))
    }
}
