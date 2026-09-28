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
        fun `앱 API 에 한국어 태그와 요약이 실린다`() {
            mockMvc.get("/v3/api-docs").andExpect {
                status { isOk() }
                jsonPath("$.tags[?(@.name == '장바구니 (앱)')]") { exists() }
                jsonPath("$.paths['/api/v1/cart/items'].post.summary") { value("장바구니 담기") }
                jsonPath("$.components.schemas.AddCartItemRequest.properties.quantity.description") { exists() }
            }
        }

        @Test
        fun `어드민·내부 API 에 한국어 태그와 요약이 실린다`() {
            mockMvc.get("/v3/api-docs").andExpect {
                status { isOk() }
                jsonPath("$.tags[?(@.name == '쿠폰 관리 (어드민)')]") { exists() }
                jsonPath("$.tags[?(@.name == '고객 (내부)')]") { exists() }
                jsonPath("$.paths['/api-admin/v1/push-campaigns/{id}/cancel'].post.summary") { value("캠페인 예약 취소") }
                jsonPath("$.components.schemas.CouponRequest.properties.discountValue.description") { exists() }
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
