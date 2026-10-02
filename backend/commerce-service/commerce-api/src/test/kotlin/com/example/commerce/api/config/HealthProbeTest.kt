package com.example.commerce.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.HealthEndpointGroups
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** 컨테이너 준비·생존 검사. 토큰 없이 열리고 readiness 는 rw(master) 데이터소스만 본다. */
@SpringBootTest
@AutoConfigureMockMvc
class HealthProbeTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val groups: HealthEndpointGroups,
        private val healthEndpoint: HealthEndpoint,
    ) {
        @Test
        fun `토큰 없이 liveness 가 UP 이다`() {
            mockMvc.get("/actuator/health/liveness").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
        }

        @Test
        fun `토큰 없이 readiness 가 UP 이다`() {
            mockMvc.get("/actuator/health/readiness").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
        }

        @Test
        fun `readiness 는 masterDb 만 보고 자동 db(레플리카 포함)와 redis 는 보지 않는다`() {
            val readiness = groups.get("readiness")!!

            assertTrue(readiness.isMember("masterDb"))
            assertTrue(readiness.isMember("readinessState"))
            assertFalse(readiness.isMember("db"))
            assertFalse(readiness.isMember("redis"))

            // show-details: never 라 HTTP 로는 구성 요소가 안 보인다. 엔드포인트 빈으로 readiness 안의 masterDb 상태를 본다.
            assertEquals(Status.UP, healthEndpoint.healthForPath("readiness", "masterDb")!!.status)
            assertNull(healthEndpoint.healthForPath("readiness", "db"))
        }
    }
