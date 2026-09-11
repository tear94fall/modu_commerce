package com.example.commerce.api.product

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `토큰이 없으면 401`() {
            mockMvc.get("/api/v1/products").andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `모두 계정 토큰이 있으면 시드 상품 4개를 돌려준다`() {
            mockMvc
                .get("/api/v1/products") { with(jwt().jwt { it.subject("11").audience(listOf("modu-commerce")) }) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(4) }
                    jsonPath("$[0].name") { value("모두 무선 이어폰") }
                    jsonPath("$[0].price") { value(89000) }
                    jsonPath("$[3].name") { value("모두 기계식 키보드") }
                }
        }

        @Test
        fun `키워드 검색과 단건 조회`() {
            mockMvc
                .get("/api/v1/products") {
                    param("q", "텀블러")
                    with(jwt())
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].name") { value("모두 텀블러 500ml") }
                }
            mockMvc
                .get("/api/v1/products/999999") { with(jwt()) }
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.message") { exists() }
                }
        }
    }
