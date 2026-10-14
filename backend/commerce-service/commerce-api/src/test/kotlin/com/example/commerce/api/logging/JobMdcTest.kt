package com.example.commerce.api.logging

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class JobMdcTest {
    @AfterEach
    fun tearDown() = MDC.clear()

    @Test
    fun `작업 동안 job 과 job- 접두 requestId 를 두고 끝나면 지운다`() {
        val result =
            JobMdc.run("push-campaigns") {
                assertEquals("push-campaigns", MDC.get(JobMdc.MDC_JOB))
                val requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID)
                assertTrue(requestId.startsWith(JobMdc.REQUEST_ID_PREFIX))
                assertEquals(JobMdc.REQUEST_ID_PREFIX.length + 16, requestId.length)
                42
            }

        assertEquals(42, result)
        assertNull(MDC.get(JobMdc.MDC_JOB))
        assertNull(MDC.get(RequestContextFilter.MDC_REQUEST_ID))
    }

    @Test
    fun `작업이 예외로 끝나도 MDC 를 지운다`() {
        runCatching { JobMdc.run("tier-monthly-run") { error("boom") } }

        assertNull(MDC.get(JobMdc.MDC_JOB))
        assertNull(MDC.get(RequestContextFilter.MDC_REQUEST_ID))
    }
}
