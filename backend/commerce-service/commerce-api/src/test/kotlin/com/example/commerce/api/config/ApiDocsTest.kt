package com.example.commerce.api.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `토큰 없이 API 문서를 받는다`() {
            mockMvc.get("/v3/api-docs").andExpect {
                status { isOk() }
                jsonPath("$.openapi") { exists() }
                jsonPath("$.info.title") { value("commerce-service") }
                jsonPath("$.info.version") { value("v1") }
                jsonPath("$.paths['/api/v1/products']") { exists() }
                jsonPath("$.paths['/api-admin/v1/push-campaigns']") { exists() }
                jsonPath("$.components.schemas.ErrorResponse") { exists() }
            }
        }

        @Test
        fun `하위 경로도 인증에 막히지 않는다`() {
            val status =
                mockMvc
                    .get("/v3/api-docs/swagger-config")
                    .andReturn()
                    .response.status
            assertTrue(status != 401 && status != 403) { "status=$status" }
        }
    }
