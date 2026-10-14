package com.example.commerce.api.logging

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestContextFilterTest {
    private val filter = RequestContextFilter()
    private lateinit var capture: LogCapture

    /** 체인 안에서 보인 MDC 값을 잡아 둔다. */
    private var seenRequestId: String? = null
    private var seenUserId: String? = null
    private val chain =
        FilterChain { _, res ->
            seenRequestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID)
            seenUserId = MDC.get(RequestContextFilter.MDC_USER_ID)
            (res as MockHttpServletResponse).status = 204
        }

    @BeforeEach
    fun setUp() {
        capture = LogCapture("http.access")
    }

    @AfterEach
    fun tearDown() {
        capture.close()
        MDC.clear()
    }

    private fun run(request: MockHttpServletRequest): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        return response
    }

    @Test
    fun `헤더가 없으면 16자 requestId 를 만들어 MDC 와 응답 헤더에 둔다`() {
        val response = run(MockHttpServletRequest("GET", "/api-public/v1/products"))

        val requestId = response.getHeader(RequestContextFilter.REQUEST_ID_HEADER)!!
        assertEquals(16, requestId.length)
        assertTrue(RequestContextFilter.isValidRequestId(requestId))
        assertEquals(requestId, seenRequestId)
        assertNull(seenUserId)
    }

    @Test
    fun `올바른 X-Request-Id 는 그대로 쓰고 X-Auth-User-Id 는 userId 로 둔다`() {
        val request =
            MockHttpServletRequest("GET", "/api-public/v1/products").apply {
                addHeader(RequestContextFilter.REQUEST_ID_HEADER, "gw-abc_123")
                addHeader(RequestContextFilter.USER_ID_HEADER, "user-7")
            }

        val response = run(request)

        assertEquals("gw-abc_123", response.getHeader(RequestContextFilter.REQUEST_ID_HEADER))
        assertEquals("gw-abc_123", seenRequestId)
        assertEquals("user-7", seenUserId)
    }

    @Test
    fun `글자나 길이가 어긋난 X-Request-Id 는 버리고 새로 만든다`() {
        listOf("bad id", "a\nb", "x".repeat(65), "<script>", "").forEach { bad ->
            val request =
                MockHttpServletRequest(
                    "GET",
                    "/api-public/v1/products",
                ).apply { addHeader(RequestContextFilter.REQUEST_ID_HEADER, bad) }

            val response = run(request)

            val requestId = response.getHeader(RequestContextFilter.REQUEST_ID_HEADER)!!
            assertNotEquals(bad, requestId)
            assertEquals(16, requestId.length)
        }
    }

    @Test
    fun `요청이 끝나면 MDC 를 비운다 — 예외가 나도`() {
        run(MockHttpServletRequest("GET", "/api-public/v1/products").apply { addHeader(RequestContextFilter.USER_ID_HEADER, "user-7") })
        assertNull(MDC.get(RequestContextFilter.MDC_REQUEST_ID))
        assertNull(MDC.get(RequestContextFilter.MDC_USER_ID))

        val failing = FilterChain { _, _ -> throw IllegalStateException("boom") }
        runCatching { filter.doFilter(MockHttpServletRequest("GET", "/api-public/v1/x"), MockHttpServletResponse(), failing) }
        assertNull(MDC.get(RequestContextFilter.MDC_REQUEST_ID))
    }

    @Test
    fun `http access 로그를 method path status durationMs 로 남긴다`() {
        run(MockHttpServletRequest("POST", "/api-public/v1/orders").apply { queryString = "a=1" })

        val event = capture.events.single()
        val fields = LogCapture.fields(event)
        assertEquals("http.access", fields["event"])
        assertEquals("POST", fields["method"])
        assertEquals("/api-public/v1/orders", fields["path"])
        assertEquals(204, fields["status"])
        assertTrue((fields["durationMs"] as Number).toLong() >= 0)
        assertTrue(event.formattedMessage.startsWith("POST /api-public/v1/orders 204 "))
    }

    @Test
    fun `actuator 와 api-docs 는 접속 로그를 남기지 않는다`() {
        run(MockHttpServletRequest("GET", "/actuator/health/liveness"))
        run(MockHttpServletRequest("GET", "/actuator/prometheus"))
        run(MockHttpServletRequest("GET", "/v3/api-docs"))

        assertTrue(capture.events.isEmpty())
    }
}
