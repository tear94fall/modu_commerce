package com.example.commerce.api.logging

import ch.qos.logback.classic.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class JobMdcTest {
    private lateinit var capture: LogCapture

    @BeforeEach
    fun setUp() {
        capture = LogCapture(JobMdc.LOGGER)
    }

    @AfterEach
    fun tearDown() {
        capture.close()
        MDC.clear()
    }

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

    /** batch-service 가 HTTP 로 돌리는 회차: RequestContextFilter 가 X-Request-Id(배치 회차 id)를 MDC 에 넣어 둔 상태다 — 그 id 를 그대로 써야 양쪽 로그가 한 id 로 묶인다. */
    @Test
    fun `MDC 에 requestId 가 이미 있으면 새로 만들지 않고 그대로 쓰고, 끝나도 지우지 않는다`() {
        MDC.put(RequestContextFilter.MDC_REQUEST_ID, "job-batch000000001")

        JobMdc.run("point-outbox-relay") {
            assertEquals("job-batch000000001", MDC.get(RequestContextFilter.MDC_REQUEST_ID))
        }

        assertEquals("job-batch000000001", MDC.get(RequestContextFilter.MDC_REQUEST_ID), "요청 범위의 id 는 필터가 지운다")
        assertNull(MDC.get(JobMdc.MDC_JOB))
        assertEquals("job-batch000000001", capture.events.single().mdcPropertyMap[RequestContextFilter.MDC_REQUEST_ID])
    }

    @Test
    fun `작업이 예외로 끝나도 MDC 를 지운다`() {
        runCatching { JobMdc.run("tier-monthly-run") { error("boom") } }

        assertNull(MDC.get(JobMdc.MDC_JOB))
        assertNull(MDC.get(RequestContextFilter.MDC_REQUEST_ID))
    }

    /** 회차마다 `job.run` 이벤트 한 줄 — OpenSearch 에서 attributes.event:"job.run" 으로 배치 실행 이력을 본다. */
    @Test
    fun `작업이 끝나면 job run 이벤트 한 줄을 남긴다 — 이름·결과 ok·소요 시간, MDC 의 job- requestId 와 함께`() {
        JobMdc.run("point-reconcile") { Thread.sleep(5) }

        val event = capture.events.single()
        val fields = LogCapture.fields(event)
        assertEquals(Level.INFO, event.level)
        assertEquals("job.run", fields["event"])
        assertEquals("point-reconcile", fields["job"])
        assertEquals("ok", fields["outcome"])
        assertTrue((fields["durationMs"] as Number).toLong() >= 5, fields["durationMs"].toString())
        assertTrue(event.mdcPropertyMap[RequestContextFilter.MDC_REQUEST_ID]!!.startsWith(JobMdc.REQUEST_ID_PREFIX))
        assertEquals("point-reconcile", event.mdcPropertyMap[JobMdc.MDC_JOB])
    }

    @Test
    fun `작업이 예외로 끝나면 ERROR job run 이벤트에 outcome failed 와 error 를 남기고 예외는 그대로 던진다`() {
        val thrown = runCatching { JobMdc.run("tier-monthly-run") { error("boom") } }.exceptionOrNull()

        assertEquals("boom", thrown?.message)
        val event = capture.events.single()
        val fields = LogCapture.fields(event)
        assertEquals(Level.ERROR, event.level)
        assertEquals("job.run", fields["event"])
        assertEquals("tier-monthly-run", fields["job"])
        assertEquals("failed", fields["outcome"])
        assertTrue((fields["error"] as String).contains("boom"), fields["error"].toString())
        assertEquals("boom", event.throwableProxy?.message)
    }
}
