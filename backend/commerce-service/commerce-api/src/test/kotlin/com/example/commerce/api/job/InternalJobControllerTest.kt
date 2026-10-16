package com.example.commerce.api.job

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `/api-internal/v1/jobs` — batch-service 가 부른다. 토큰 검사는 InternalApiFilter 가 하므로 여기서는 가짜 Job 과 JobRunner 로
 * 응답 모양·상태 코드만 본다(standalone MockMvc).
 */
class InternalJobControllerTest {
    private class FakeJob(
        override val name: String,
        override val description: String,
        private val body: () -> Map<String, Any?>,
    ) : Job {
        override fun run(): Map<String, Any?> = body()
    }

    private val release = CountDownLatch(1)
    private val started = CountDownLatch(1)
    private val runner =
        JobRunner(
            listOf(
                FakeJob("ok-job", "잘 되는 잡") { mapOf("count" to 3) },
                FakeJob("boom-job", "터지는 잡") { error("boom") },
                FakeJob("slow-job", "오래 걸리는 잡") {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    emptyMap()
                },
            ),
        )
    private val mockMvc = MockMvcBuilders.standaloneSetup(InternalJobController(runner)).build()

    @AfterEach
    fun tearDown() = MDC.clear()

    @Test
    fun `잡 목록 — 이름과 설명, 이름순`() {
        mockMvc.get("/api-internal/v1/jobs").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].name") { value("boom-job") }
            jsonPath("$[0].description") { value("터지는 잡") }
            jsonPath("$[1].name") { value("ok-job") }
        }
    }

    @Test
    fun `한 회차 실행 — 200, outcome ok, 결과와 job- requestId`() {
        mockMvc.post("/api-internal/v1/jobs/ok-job/run").andExpect {
            status { isOk() }
            jsonPath("$.job") { value("ok-job") }
            jsonPath("$.outcome") { value("ok") }
            jsonPath("$.result.count") { value(3) }
            jsonPath("$.requestId") { value(org.hamcrest.Matchers.startsWith("job-")) }
            jsonPath("$.durationMs") { isNumber() }
            jsonPath("$.error") { doesNotExist() }
        }
    }

    @Test
    fun `잡이 실패하면 500 에 같은 모양으로 outcome failed 와 error`() {
        mockMvc.post("/api-internal/v1/jobs/boom-job/run").andExpect {
            status { isInternalServerError() }
            jsonPath("$.job") { value("boom-job") }
            jsonPath("$.outcome") { value("failed") }
            jsonPath("$.error") { value(org.hamcrest.Matchers.containsString("boom")) }
            jsonPath("$.result") { doesNotExist() }
        }
    }

    @Test
    fun `모르는 잡은 404, 본문에 이름을 되돌려 주지 않는다`() {
        mockMvc.post("/api-internal/v1/jobs/<script>/run").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("unknown job") }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))) }
        }
    }

    @Test
    fun `돌고 있는 잡을 다시 부르면 409`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            val first = pool.submit { mockMvc.post("/api-internal/v1/jobs/slow-job/run").andExpect { status { isOk() } } }
            started.await(5, TimeUnit.SECONDS)

            mockMvc.post("/api-internal/v1/jobs/slow-job/run").andExpect {
                status { isConflict() }
                jsonPath("$.message") { value("job already running") }
            }

            release.countDown()
            first.get(5, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }
}
