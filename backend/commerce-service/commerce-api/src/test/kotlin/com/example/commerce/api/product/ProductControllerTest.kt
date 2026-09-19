package com.example.commerce.api.product

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.seed.ProductSeeder
import org.junit.jupiter.api.BeforeEach
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
        private val support: CatalogTestSupport,
    ) {
        @BeforeEach
        fun reseed() = support.reseed()

        @Test
        fun `토큰이 없으면 401`() {
            mockMvc.get("/api/v1/products").andExpect { status { isUnauthorized() } }
            mockMvc.get("/api/v1/categories").andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `목록은 최신순 페이지로 준다`() {
            mockMvc
                .get("/api/v1/products") { with(jwt().jwt { it.subject("11").audience(listOf("modu-commerce")) }) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.content.length()") { value(20) }
                    jsonPath("$.totalElements") { value(ProductSeeder.SAMPLE_COUNT) }
                    jsonPath("$.totalPages") { value(2) }
                    jsonPath("$.content[0].name") { value("모두 스티커 팩") }
                    jsonPath("$.content[0].wished") { value(false) }
                    jsonPath("$.content[0].soldOut") { value(false) }
                }
        }

        @Test
        fun `카테고리 정렬 검색`() {
            val fashion = support.categoryId("패션")
            mockMvc
                .get("/api/v1/products") {
                    param("categoryId", fashion.toString())
                    param("sort", "priceAsc")
                    with(jwt())
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.totalElements") { value(6) }
                    jsonPath("$.content[0].name") { value("모두 베이직 티셔츠") }
                }
            mockMvc
                .get("/api/v1/products") {
                    param("q", "텀블러")
                    with(jwt())
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.totalElements") { value(1) }
                    jsonPath("$.content[0].name") { value("모두 텀블러 500ml") }
                }
            mockMvc
                .get("/api/v1/products") {
                    param("q", "버킷햇")
                    with(jwt())
                }.andExpect { jsonPath("$.content[0].soldOut") { value(true) } }
            mockMvc
                .get("/api/v1/products") {
                    param("categoryId", "999999")
                    with(jwt())
                }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `상세는 사진 옵션 SKU 카테고리 경로를 준다`() {
            val id = support.productId("모두 베이직 티셔츠")
            mockMvc
                .get("/api/v1/products/$id") { with(jwt()) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.images.length()") { value(2) }
                    jsonPath("$.imageUrl") { value("https://picsum.photos/seed/modu-tshirt-1/600/600") }
                    jsonPath("$.categoryPath[0]") { value("패션") }
                    jsonPath("$.categoryPath[1]") { value("의류") }
                    jsonPath("$.optionGroups.length()") { value(2) }
                    jsonPath("$.optionGroups[0].name") { value("색상") }
                    jsonPath("$.optionGroups[0].values.length()") { value(3) }
                    jsonPath("$.skus.length()") { value(6) }
                    jsonPath("$.skus[0].optionValueIds.length()") { value(2) }
                    jsonPath("$.skus[0].optionLabel") { value("블랙 / M") }
                    jsonPath("$.detail") { exists() }
                    jsonPath("$.wished") { value(false) }
                }
            mockMvc.get("/api/v1/products/999999") { with(jwt()) }.andExpect {
                status { isNotFound() }
                jsonPath("$.message") { exists() }
            }
        }

        @Test
        fun `할인율과 카테고리 트리`() {
            val id = support.productId("모두 무선 이어폰")
            mockMvc
                .get("/api/v1/products/$id") { with(jwt()) }
                .andExpect {
                    jsonPath("$.price") { value(89000) }
                    jsonPath("$.listPrice") { value(109000) }
                    jsonPath("$.discountRate") { value(18) }
                }
            mockMvc
                .get("/api/v1/categories") { with(jwt()) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(4) }
                    jsonPath("$[0].name") { value("전자기기") }
                    jsonPath("$[0].children.length()") { value(3) }
                    jsonPath("$[0].productCount") { doesNotExist() }
                }
        }
    }
