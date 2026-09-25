package com.example.commerce.api.cart

import com.example.commerce.api.support.CatalogTestSupport
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
    ) {
        private val me = jwt().jwt { it.subject("11") }
        private val other = jwt().jwt { it.subject("22") }

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        private fun add(
            user: org.springframework.test.web.servlet.request.RequestPostProcessor,
            skuId: Long,
            quantity: Int,
        ) = mockMvc.post("/api/v1/cart/items") {
            with(user)
            contentType = MediaType.APPLICATION_JSON
            content = """{"skuId":$skuId,"quantity":$quantity}"""
        }

        @Test
        fun `담기 합산 수량 변경 삭제와 합계`() {
            val blackM = support.skuId("모두 베이직 티셔츠", "블랙 / M")
            val mug = support.skuId("모두 머그컵 세트")

            add(me, blackM, 2).andExpect {
                status { isOk() }
                jsonPath("$.optionLabel") { value("블랙 / M") }
                jsonPath("$.quantity") { value(2) }
                jsonPath("$.unitPrice") { value(19000) }
            }
            add(me, blackM, 1).andExpect { jsonPath("$.quantity") { value(3) } }
            add(me, mug, 1).andExpect { status { isOk() } }

            val cart =
                mockMvc
                    .get("/api/v1/cart") { with(me) }
                    .andExpect {
                        status { isOk() }
                        jsonPath("$.items.length()") { value(2) }
                        jsonPath("$.itemCount") { value(2) }
                        jsonPath("$.totalAmount") { value(19000 * 3 + 18000) }
                        jsonPath("$.items[1].productName") { value("모두 베이직 티셔츠") }
                        jsonPath("$.items[1].available") { value(true) }
                    }.andReturn()
            val itemId = JsonPath.read<Int>(cart.response.contentAsString, "$.items[1].id")

            mockMvc
                .patch("/api/v1/cart/items/$itemId") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"quantity":5}"""
                }.andExpect { jsonPath("$.quantity") { value(5) } }

            // 남의 장바구니 줄은 못 본다
            mockMvc.delete("/api/v1/cart/items/$itemId") { with(other) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api/v1/cart") { with(other) }.andExpect { jsonPath("$.itemCount") { value(0) } }

            mockMvc.delete("/api/v1/cart/items/$itemId") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api/v1/cart") { with(me) }.andExpect { jsonPath("$.itemCount") { value(1) } }
        }

        @Test
        fun `검증 실패와 없는 옵션`() {
            add(me, support.skuId("모두 머그컵 세트"), 0).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("quantity: 수량은 1 이상이어야 합니다.") }
            }
            add(me, 999_999L, 1).andExpect { status { isNotFound() } }
            add(me, support.skuId("모두 머그컵 세트"), 99).andExpect { status { isOk() } }
            add(me, support.skuId("모두 머그컵 세트"), 1).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("수량은 1~99 사이여야 합니다.") }
            }
        }
    }
