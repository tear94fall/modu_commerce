package com.example.commerce.api.wishlist

import com.example.commerce.api.support.CatalogTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class WishlistControllerTest
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

        @Test
        fun `찜 추가 해제는 멱등이고 목록과 상세에 반영된다`() {
            val mug = support.productId("모두 머그컵 세트")
            val tumbler = support.productId("모두 텀블러 500ml")

            mockMvc.post("/api/v1/wishlist/$mug") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.post("/api/v1/wishlist/$mug") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.post("/api/v1/wishlist/$tumbler") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.post("/api/v1/wishlist/999999") { with(me) }.andExpect { status { isNotFound() } }
            // 다른 사람도 텀블러를 찜하면 텀블러(2) > 머그(1) 로 인기순이 갈린다
            mockMvc.post("/api/v1/wishlist/$tumbler") { with(other) }.andExpect { status { isNoContent() } }

            val list =
                mockMvc
                    .get("/api/v1/wishlist") { with(me) }
                    .andReturn()
                    .response.contentAsString
            check(list.contains("\"totalElements\":2")) { "wishlist: $list" }
            check(list.indexOf("모두 텀블러 500ml") < list.indexOf("모두 머그컵 세트")) { "wishlist order: $list" }
            mockMvc.get("/api/v1/wishlist") { with(other) }.andExpect { jsonPath("$.totalElements") { value(1) } }

            mockMvc
                .get("/api/v1/products/$mug") { with(me) }
                .andExpect {
                    jsonPath("$.wished") { value(true) }
                    jsonPath("$.wishCount") { value(1) }
                }
            mockMvc
                .get("/api/v1/products") {
                    param("q", "머그")
                    with(me)
                }.andExpect { jsonPath("$.content[0].wished") { value(true) } }
            mockMvc
                .get("/api/v1/products") {
                    param("sort", "popular")
                    param("size", "1")
                    with(other)
                }.andExpect {
                    jsonPath("$.content[0].name") { value("모두 텀블러 500ml") }
                    jsonPath("$.content[0].wished") { value(true) }
                }

            mockMvc.delete("/api/v1/wishlist/$mug") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.delete("/api/v1/wishlist/$mug") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api/v1/wishlist") { with(me) }.andExpect { jsonPath("$.totalElements") { value(1) } }
            mockMvc.get("/api/v1/products/$mug") { with(me) }.andExpect { jsonPath("$.wishCount") { value(0) } }
        }
    }
