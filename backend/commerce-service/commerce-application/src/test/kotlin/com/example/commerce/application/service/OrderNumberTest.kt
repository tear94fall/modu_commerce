package com.example.commerce.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OrderNumberTest {
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun `이미 있는 주문번호면 다시 뽑는다`() {
        val candidates = ArrayDeque(listOf("20261009-AAAAAA", "20261009-BBBBBB", "20261009-CCCCCC"))
        val taken = setOf("20261009-AAAAAA", "20261009-BBBBBB")

        val no = OrderCommandService.nextOrderNo(today, { candidates.removeFirst() }) { it in taken }

        assertEquals("20261009-CCCCCC", no)
    }

    @Test
    fun `세 번 모두 겹치면 포기한다`() {
        var tries = 0
        assertThrows(IllegalStateException::class.java) {
            OrderCommandService.nextOrderNo(today, { "20261009-AAAAAA".also { tries++ } }) { true }
        }
        assertEquals(3, tries)
    }
}
