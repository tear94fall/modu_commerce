package com.example.commerce.api.job

import com.example.commerce.api.logging.JobMdc
import com.example.commerce.api.logging.LogCapture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JobRunnerTest {
    private class FakeJob(
        override val name: String,
        private val body: () -> Map<String, Any?>,
    ) : Job {
        override val description = "test $name"

        override fun run(): Map<String, Any?> = body()
    }

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
    fun `잡을 이름으로 찾아 돌리고 결과·소요 시간·job- requestId 를 돌려준다, job run 이벤트는 한 줄`() {
        val runner = JobRunner(listOf(FakeJob("ok-job") { mapOf("count" to 3) }))

        val run = runner.run("ok-job")

        assertEquals("ok-job", run.job)
        assertEquals("ok", run.outcome)
        assertEquals(mapOf("count" to 3), run.result)
        assertNull(run.error)
        assertTrue(run.durationMs >= 0)
        assertTrue(run.requestId.startsWith(JobMdc.REQUEST_ID_PREFIX), run.requestId)
        assertEquals(1, capture.events.size)
        assertEquals("ok", LogCapture.fields(capture.events[0])["outcome"])
    }

    @Test
    fun `잡이 예외로 끝나면 던지지 않고 outcome failed 와 error 를 돌려주고, 그 뒤 다시 돌릴 수 있다`() {
        var calls = 0
        val runner =
            JobRunner(
                listOf(
                    FakeJob("boom-job") {
                        calls++
                        if (calls == 1) error("boom") else mapOf("count" to 1)
                    },
                ),
            )

        val failed = runner.run("boom-job")
        assertEquals("failed", failed.outcome)
        assertNull(failed.result)
        assertTrue(failed.error!!.contains("boom"), failed.error)
        assertTrue(failed.requestId.startsWith(JobMdc.REQUEST_ID_PREFIX))

        assertEquals("ok", runner.run("boom-job").outcome)
        assertNull(MDC.get(JobMdc.MDC_JOB))
    }

    @Test
    fun `모르는 잡 이름은 UnknownJobException`() {
        val runner = JobRunner(listOf(FakeJob("ok-job") { emptyMap() }))

        assertThrows(UnknownJobException::class.java) { runner.run("nope") }
        assertEquals(listOf("ok-job"), runner.all().map { it.name })
    }

    @Test
    fun `같은 잡이 돌고 있으면 두 번째는 JobAlreadyRunningException, 끝나면 다시 돌 수 있다`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val runner =
            JobRunner(
                listOf(
                    FakeJob("slow-job") {
                        started.countDown()
                        release.await(5, TimeUnit.SECONDS)
                        mapOf("count" to 1)
                    },
                ),
            )
        val pool = Executors.newSingleThreadExecutor()
        try {
            val first = pool.submit<JobRun> { runner.run("slow-job") }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            assertThrows(JobAlreadyRunningException::class.java) { runner.run("slow-job") }

            release.countDown()
            assertEquals("ok", first.get(5, TimeUnit.SECONDS).outcome)
            assertEquals("ok", runner.run("slow-job").outcome)
        } finally {
            pool.shutdownNow()
        }
    }

    /** 전환 기간에는 batch-service 의 HTTP 회차와 같은 파드의 cron 이 겹칠 수 있다 — 스케줄러는 던지지 않고 WARN 한 줄로 건너뛴다. */
    @Test
    fun `runIfIdle 은 같은 잡이 돌고 있으면 null 을 돌려주고 던지지 않는다`() {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val runner =
            JobRunner(
                listOf(
                    FakeJob("slow") {
                        started.countDown()
                        release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                        mapOf("n" to 1)
                    },
                ),
            )
        val pool =
            java.util.concurrent.Executors
                .newSingleThreadExecutor()
        try {
            val first = pool.submit<JobRun> { runner.run("slow") }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))

            assertNull(runner.runIfIdle("slow"))

            release.countDown()
            assertEquals("ok", first.get(5, java.util.concurrent.TimeUnit.SECONDS).outcome)
            assertEquals("ok", runner.runIfIdle("slow")!!.outcome)
        } finally {
            pool.shutdownNow()
        }
    }
}
