package com.example.commerce.api.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Prometheus 수집 엔드포인트와 요청 컨텍스트 필터가 실제 필터 체인(Spring Security 포함)에서 도는지 본다.
 * @SpringBootTest 는 기본으로 메트릭 내보내기를 끈다(prometheus 엔드포인트 404). AutoConfigureObservability 로 실제 기동과 같게 켠다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ObservabilityEndpointsTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `토큰 없이 actuator prometheus 를 긁을 수 있다`() {
            val body =
                mockMvc
                    .get("/actuator/prometheus")
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString

            assertTrue(body.contains("jvm_memory_used_bytes"), body.take(200))
        }

        @Test
        fun `security 가 거절한 요청도 X-Request-Id 를 돌려주고 보낸 값은 그대로 쓰며 api access 이벤트가 남는다`() {
            LogCapture("api.access").use { capture ->
                mockMvc
                    .get("/api-public/v1/cart") { header(RequestContextFilter.REQUEST_ID_HEADER, "gw-41") }
                    .andExpect { status { isUnauthorized() } }

                val fields = LogCapture.fields(capture.events.single())
                assertEquals(401, fields["status"])
                assertEquals("/api-public/v1/cart", fields["path"])
                assertEquals("gw-41", capture.events.single().mdcPropertyMap[RequestContextFilter.MDC_REQUEST_ID])
            }

            val generated =
                mockMvc
                    .get("/api-public/v1/cart")
                    .andExpect { status { isUnauthorized() } }
                    .andReturn()
                    .response
                    .getHeader(RequestContextFilter.REQUEST_ID_HEADER)!!
            assertEquals(16, generated.length)

            val echoed =
                mockMvc
                    .get("/api-public/v1/cart") { header(RequestContextFilter.REQUEST_ID_HEADER, "gw-42") }
                    .andReturn()
                    .response
                    .getHeader(RequestContextFilter.REQUEST_ID_HEADER)
            assertEquals("gw-42", echoed)
        }
    }
