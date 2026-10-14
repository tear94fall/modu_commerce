package com.example.commerce.application.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class PointOutboxTest {
    private val now = LocalDateTime.of(2026, 10, 9, 0, 0)

    @Test
    fun `대기 시간은 1분부터 두 배씩 늘고 1시간에서 멈춘다`() {
        assertEquals(Duration.ofMinutes(1), PointOutbox.backoff(1))
        assertEquals(Duration.ofMinutes(2), PointOutbox.backoff(2))
        assertEquals(Duration.ofMinutes(4), PointOutbox.backoff(3))
        assertEquals(Duration.ofMinutes(32), PointOutbox.backoff(6))
        assertEquals(Duration.ofHours(1), PointOutbox.backoff(7))
        assertEquals(Duration.ofHours(1), PointOutbox.backoff(19))
    }

    @Test
    fun `20번 실패하면 FAILED 이고 오류는 300자로 자른다`() {
        val row = PointOutbox(PointOutboxKind.REFUND, "u1", "20261009-AAAAAA", "order:20261009-AAAAAA", 1000, now)
        repeat(19) { assertFalse(row.retryLater("boom", now)) }
        assertEquals(PointOutboxStatus.PENDING, row.status)
        assertEquals(now.plusHours(1), row.nextAttemptAt)

        assertTrue(row.retryLater("x".repeat(400), now))
        assertEquals(PointOutboxStatus.FAILED, row.status)
        assertEquals(20, row.attempts)
        assertEquals(300, row.lastError?.length)
    }
}
