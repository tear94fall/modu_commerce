package com.example.commerce.api.review

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.member.MemberLookup
import com.example.commerce.application.member.MemberProfile
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.RequestPostProcessor

@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
    ) {
        @MockitoBean
        private lateinit var memberLookup: MemberLookup

        private val me = jwt().jwt { it.subject("11") }
        private val other = jwt().jwt { it.subject("22") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        @BeforeEach
        fun members() {
            given(memberLookup.find("11")).willReturn(MemberProfile("11", "임준섭", "me@modu.local"))
            given(memberLookup.find("22")).willReturn(null)
        }

        /** 주문을 만들고 첫 줄의 orderItemId 를 돌려준다. */
        private fun orderItem(
            user: RequestPostProcessor,
            productName: String,
            optionLabel: String = "",
        ): Pair<Int, Int> {
            val addressId =
                JsonPath.read<Int>(
                    mockMvc
                        .post("/api/v1/addresses") {
                            with(user)
                            contentType = MediaType.APPLICATION_JSON
                            content =
                                """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구","address2":null}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            val body =
                mockMvc
                    .post("/api/v1/orders") {
                        with(user)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"addressId":$addressId,"items":[{"skuId":${support.skuId(productName, optionLabel)},"quantity":1}]}"""
                    }.andReturn()
                    .response.contentAsString
            return JsonPath.read<Int>(body, "$.id") to JsonPath.read<Int>(body, "$.items[0].id")
        }

        private fun write(
            user: RequestPostProcessor,
            orderItemId: Int,
            rating: Int,
            text: String,
        ) = mockMvc.post("/api/v1/reviews") {
            with(user)
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderItemId":$orderItemId,"rating":$rating,"content":"$text"}"""
        }

        @Test
        fun `구매한 주문 줄에 리뷰를 쓰면 상품 평점과 목록에 반영된다`() {
            val productId = support.productId("모두 머그컵 세트")
            val (orderId, itemId) = orderItem(me, "모두 머그컵 세트")

            mockMvc.get("/api/v1/reviews/targets/$itemId") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.reviewable") { value(true) }
                jsonPath("$.productName") { value("모두 머그컵 세트") }
            }
            mockMvc.get("/api/v1/reviews/targets/$itemId") { with(other) }.andExpect { status { isNotFound() } }

            val reviewId =
                JsonPath.read<Int>(
                    write(me, itemId, 5, "머그컵이 튼튼하고 색이 예뻐요")
                        .andExpect {
                            status { isCreated() }
                            jsonPath("$.authorName") { value("임준섭") }
                            jsonPath("$.mine") { value(true) }
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )

            // 같은 줄에 두 번은 안 된다
            write(me, itemId, 4, "두 번째 리뷰는 안 돼요").andExpect { status { isBadRequest() } }
            // 남의 주문 줄은 404
            write(other, itemId, 4, "남의 주문 줄에는 못 씁니다").andExpect { status { isNotFound() } }

            mockMvc.get("/api/v1/products/$productId") { with(other) }.andExpect {
                jsonPath("$.reviewCount") { value(1) }
                jsonPath("$.ratingAverage") { value(5.0) }
            }
            mockMvc.get("/api/v1/products/$productId/reviews") { with(other) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].authorName") { value("임*섭") }
                jsonPath("$.content[0].authorEmail") { value(null) }
                jsonPath("$.content[0].mine") { value(false) }
            }
            mockMvc.get("/api/v1/products/$productId/reviews/summary") { with(other) }.andExpect {
                jsonPath("$.count") { value(1) }
                jsonPath("$.average") { value(5.0) }
                jsonPath("$.distribution[0].rating") { value(5) }
                jsonPath("$.distribution[0].count") { value(1) }
                jsonPath("$.distribution[4].count") { value(0) }
            }
            mockMvc.get("/api/v1/orders/$orderId") { with(me) }.andExpect {
                jsonPath("$.items[0].reviewId") { value(reviewId) }
                jsonPath("$.items[0].reviewable") { value(false) }
            }
            mockMvc.get("/api/v1/reviews/targets/$itemId") { with(me) }.andExpect {
                jsonPath("$.reviewable") { value(false) }
                jsonPath("$.reviewId") { value(reviewId) }
            }
        }

        @Test
        fun `수정하면 평점이 바뀌고 삭제하면 집계에서 빠진다`() {
            val productId = support.productId("모두 머그컵 세트")
            val (_, mine) = orderItem(me, "모두 머그컵 세트")
            val (_, theirs) = orderItem(other, "모두 머그컵 세트")
            val id = JsonPath.read<Int>(write(me, mine, 5, "머그컵이 튼튼하고 색이 예뻐요").andReturn().response.contentAsString, "$.id")
            write(other, theirs, 3, "생각보다 작아요 그래도 괜찮아요").andExpect { status { isCreated() } }

            mockMvc.get("/api/v1/products/$productId") { with(me) }.andExpect { jsonPath("$.ratingAverage") { value(4.0) } }

            mockMvc
                .put("/api/v1/reviews/$id") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"rating":1,"content":"쓰다 보니 손잡이가 깨졌어요"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.rating") { value(1) }
                }
            mockMvc.get("/api/v1/products/$productId") { with(me) }.andExpect { jsonPath("$.ratingAverage") { value(2.0) } }
            mockMvc.get("/api/v1/products/$productId/reviews?sort=high") { with(me) }.andExpect {
                jsonPath("$.content[0].rating") { value(3) }
                jsonPath("$.content[1].rating") { value(1) }
            }

            // 남이 내 리뷰를 수정·삭제할 수는 없다
            mockMvc
                .put("/api/v1/reviews/$id") {
                    with(other)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"rating":5,"content":"남의 리뷰를 고칠 수는 없어요"}"""
                }.andExpect { status { isNotFound() } }
            mockMvc.delete("/api/v1/reviews/$id") { with(other) }.andExpect { status { isNotFound() } }

            mockMvc.get("/api/v1/me/reviews") { with(me) }.andExpect {
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].productName") { value("모두 머그컵 세트") }
            }
            mockMvc.delete("/api/v1/reviews/$id") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api/v1/me/reviews") { with(me) }.andExpect { jsonPath("$.totalElements") { value(0) } }
            mockMvc.get("/api/v1/products/$productId") { with(me) }.andExpect {
                jsonPath("$.reviewCount") { value(1) }
                jsonPath("$.ratingAverage") { value(3.0) }
            }
        }

        @Test
        fun `취소한 주문과 짧은 내용은 거절한다`() {
            val (orderId, itemId) = orderItem(me, "모두 머그컵 세트")
            write(me, itemId, 5, "짧아요").andExpect { status { isBadRequest() } }
            write(me, itemId, 6, "별점이 범위를 벗어났습니다").andExpect { status { isBadRequest() } }
            mockMvc.post("/api/v1/orders/$orderId/cancel") { with(me) }.andExpect { status { isOk() } }
            write(me, itemId, 5, "취소한 주문에는 못 씁니다").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("취소된 주문의 상품에는 리뷰를 쓸 수 없습니다.") }
            }
            mockMvc.get("/api/v1/reviews/targets/$itemId") { with(me) }.andExpect { jsonPath("$.reviewable") { value(false) } }
        }

        @Test
        fun `관리자가 숨기면 앱 목록과 평점에서 빠지고 본인에게는 사유가 보인다`() {
            val productId = support.productId("모두 머그컵 세트")
            val (_, itemId) = orderItem(other, "모두 머그컵 세트")
            val id = JsonPath.read<Int>(write(other, itemId, 2, "배송이 느리고 포장이 찢어져 왔어요").andReturn().response.contentAsString, "$.id")

            mockMvc.get("/api-admin/v1/reviews?q=포장") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].authorName") { value("모두 회원") }
                jsonPath("$.content[0].userId") { value("22") }
            }
            mockMvc.get("/api-admin/v1/reviews?rating=5") { with(admin) }.andExpect { jsonPath("$.totalElements") { value(0) } }
            mockMvc.get("/api-admin/v1/reviews") { with(me) }.andExpect { status { isForbidden() } }

            mockMvc
                .patch("/api-admin/v1/reviews/$id/hidden") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"hidden":true,"reason":"배송 문의는 고객센터로"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.hidden") { value(true) }
                }
            mockMvc.get("/api/v1/products/$productId/reviews") { with(me) }.andExpect { jsonPath("$.totalElements") { value(0) } }
            mockMvc.get("/api/v1/products/$productId") { with(me) }.andExpect { jsonPath("$.reviewCount") { value(0) } }
            mockMvc.get("/api/v1/me/reviews") { with(other) }.andExpect {
                jsonPath("$.content[0].hidden") { value(true) }
                jsonPath("$.content[0].hiddenReason") { value("배송 문의는 고객센터로") }
            }
            mockMvc.get("/api-admin/v1/reviews?hidden=true") { with(admin) }.andExpect { jsonPath("$.totalElements") { value(1) } }

            mockMvc
                .patch("/api-admin/v1/reviews/$id/hidden") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"hidden":false}"""
                }.andExpect { jsonPath("$.hidden") { value(false) } }
            mockMvc.get("/api/v1/products/$productId") { with(me) }.andExpect {
                jsonPath("$.reviewCount") { value(1) }
                jsonPath("$.ratingAverage") { value(2.0) }
            }

            mockMvc.get("/api-admin/v1/reviews/$id") { with(admin) }.andExpect { jsonPath("$.content") { value("배송이 느리고 포장이 찢어져 왔어요") } }
            mockMvc.delete("/api-admin/v1/reviews/$id") { with(admin) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api-admin/v1/reviews/$id") { with(admin) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api/v1/products/$productId") { with(me) }.andExpect { jsonPath("$.reviewCount") { value(0) } }
        }
    }
