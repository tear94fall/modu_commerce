package com.example.commerce.api.stock

import com.example.commerce.api.support.CatalogTestSupport
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * 재고 경고 — 품절 상품, 부족 SKU, 과다 SKU 를 나눠 주고 줄마다 30일 판매 수량을 붙인다.
 * 품절 상품의 SKU 는 부족 목록에 다시 넣지 않고, 숨긴 상품은 빠진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StockAlertsTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
    ) {
        private val me = jwt().jwt { it.subject("11") }
        private val staff = jwt().jwt { it.subject("staff-1") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        @Test
        fun `품절 상품은 따로, 부족 SKU 는 적은 순, 과다 SKU 는 많은 순`() {
            // 시드: 버킷햇 0(품절), 티셔츠 네이비/L 0(옵션만 품절), 후드 집업 XL 5 · M 8 · L 8 / 스티커 팩 100, 젤펜 90, 샤워 타월 80
            mockMvc.get("/api-admin/v1/stock/alerts?low=8&high=80") { with(staff) }.andExpect {
                status { isOk() }
                jsonPath("$.low") { value(8) }
                jsonPath("$.high") { value(80) }
                jsonPath("$.salesDays") { value(30) }
                jsonPath("$.sellingProducts") { value(24) }
                jsonPath("$.soldOut.length()") { value(1) }
                jsonPath("$.soldOut[0].name") { value("모두 버킷햇") }
                jsonPath("$.soldOut[0].skuCount") { value(1) }
                jsonPath("$.soldOut[0].sold") { value(0) }
                jsonPath("$.lowStock.length()") { value(4) }
                jsonPath("$.lowStock[0].productName") { value("모두 베이직 티셔츠") }
                jsonPath("$.lowStock[0].optionLabel") { value("네이비 / L") }
                jsonPath("$.lowStock[0].stock") { value(0) }
                jsonPath("$.lowStock[1].optionLabel") { value("XL") }
                jsonPath("$.lowStock[1].stock") { value(5) }
                jsonPath("$.lowStock[?(@.productName == '모두 버킷햇')]") { isEmpty() }
                jsonPath("$.overstock.length()") { value(3) }
                jsonPath("$.overstock[0].productName") { value("모두 스티커 팩") }
                jsonPath("$.overstock[0].stock") { value(100) }
                jsonPath("$.overstock[2].productName") { value("모두 샤워 타월") }
            }
        }

        @Test
        fun `기준을 안 주면 설정 기본값, 범위 밖이거나 과다가 부족보다 작으면 400`() {
            mockMvc.get("/api-admin/v1/stock/alerts") { with(staff) }.andExpect {
                status { isOk() }
                jsonPath("$.low") { value(5) }
                jsonPath("$.high") { value(100) }
                jsonPath("$.lowStock.length()") { value(2) }
                jsonPath("$.overstock.length()") { value(1) }
            }
            mockMvc.get("/api-admin/v1/stock/alerts?low=1001") { with(staff) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("부족 기준은 0~1000 사이여야 합니다.") }
            }
            mockMvc.get("/api-admin/v1/stock/alerts?low=50&high=50") { with(staff) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("과다 기준은 부족 기준보다 커야 합니다.") }
            }
        }

        @Test
        fun `최근 30일 판매 수량이 줄마다 붙고, 취소한 주문은 빠진다`() {
            val addressId: Int =
                JsonPath.read(
                    mockMvc
                        .post("/api-public/v1/addresses") {
                            with(me)
                            contentType = MediaType.APPLICATION_JSON
                            content =
                                """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구","address2":"101동"}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            val sticker = support.skuId("모두 스티커 팩")

            fun order(quantity: Int): Int =
                JsonPath.read(
                    mockMvc
                        .post("/api-public/v1/orders") {
                            with(me)
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"addressId":$addressId,"items":[{"skuId":$sticker,"quantity":$quantity}]}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            order(3)
            val cancelled = order(2)
            mockMvc.post("/api-public/v1/orders/$cancelled/cancel") { with(me) }.andExpect { status { isOk() } }

            // 스티커 팩 100 → 95 (취소분 2 는 복구). 과다 기준 90 이상
            mockMvc.get("/api-admin/v1/stock/alerts?high=90") { with(staff) }.andExpect {
                status { isOk() }
                jsonPath("$.overstock[0].productName") { value("모두 스티커 팩") }
                jsonPath("$.overstock[0].stock") { value(97) }
                jsonPath("$.overstock[0].sold") { value(3) }
                jsonPath("$.overstock[1].productName") { value("모두 젤펜 5색") }
                jsonPath("$.overstock[1].sold") { value(0) }
            }
        }

        @Test
        fun `숨긴 상품은 품절이어도 경고에 들어가지 않는다`() {
            val id = support.productId("모두 버킷햇")
            mockMvc
                .put("/api-admin/v1/products/$id") {
                    with(staff)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"name":"모두 버킷햇","description":"여름용 린넨 버킷햇","price":25000,"imageUrl":null,"status":"HIDDEN",
                      "categoryId":null,"listPrice":null,"detail":null,"images":[],"optionGroups":[],"skus":[{"options":{},"extraPrice":0,"stock":0}]}"""
                }.andExpect { status { isOk() } }
            mockMvc.get("/api-admin/v1/stock/alerts?low=0") { with(staff) }.andExpect {
                status { isOk() }
                jsonPath("$.soldOut.length()") { value(0) }
                jsonPath("$.sellingProducts") { value(23) }
            }
        }
    }
