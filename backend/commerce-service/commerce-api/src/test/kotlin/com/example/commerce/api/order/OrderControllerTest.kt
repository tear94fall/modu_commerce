package com.example.commerce.api.order

import com.example.commerce.api.support.CatalogTestSupport
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
    ) {
        private val me = jwt().jwt { it.subject("11") }
        private val other = jwt().jwt { it.subject("22") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        private fun address(user: RequestPostProcessor): Int =
            JsonPath.read(
                mockMvc
                    .post("/api/v1/addresses") {
                        with(user)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구","address2":"101동"}"""
                    }.andReturn()
                    .response.contentAsString,
                "$.id",
            )

        private fun addCart(
            skuId: Long,
            quantity: Int,
        ): Int =
            JsonPath.read(
                mockMvc
                    .post("/api/v1/cart/items") {
                        with(me)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"skuId":$skuId,"quantity":$quantity}"""
                    }.andReturn()
                    .response.contentAsString,
                "$.id",
            )

        @Test
        fun `장바구니에서 주문하면 재고가 줄고 장바구니가 비고 내역에 남는다`() {
            val addressId = address(me)
            val blackM = support.skuId("모두 베이직 티셔츠", "블랙 / M")
            val mug = support.skuId("모두 머그컵 세트")
            val cartA = addCart(blackM, 2)
            val cartB = addCart(mug, 1)

            val created =
                mockMvc
                    .post("/api/v1/orders") {
                        with(me)
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"addressId":$addressId,"items":[{"skuId":$blackM,"quantity":2},{"skuId":$mug,"quantity":1}],"cartItemIds":[$cartA,$cartB]}"""
                    }.andExpect {
                        status { isCreated() }
                        jsonPath("$.status") { value("PAID") }
                        jsonPath("$.totalAmount") { value(19000 * 2 + 18000) }
                        jsonPath("$.items.length()") { value(2) }
                        jsonPath("$.items[1].optionLabel") { value("블랙 / M") } // 항목은 SKU id 순(머그컵이 먼저)
                        jsonPath("$.recipient") { value("임준섭") }
                        jsonPath("$.paymentMethod") { value("MOCK") }
                        jsonPath("$.orderNo") { exists() }
                    }.andReturn()
            val orderId = JsonPath.read<Int>(created.response.contentAsString, "$.id")

            assertEquals(8, support.stockOf("모두 베이직 티셔츠", "블랙 / M"))
            assertEquals(44, support.stockOf("모두 머그컵 세트"))
            mockMvc.get("/api/v1/cart") { with(me) }.andExpect { jsonPath("$.itemCount") { value(0) } }

            mockMvc.get("/api/v1/orders") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].itemCount") { value(3) }
                jsonPath("$.content[0].firstItemName") { value("모두 머그컵 세트") }
            }
            mockMvc.get("/api/v1/orders/$orderId") { with(other) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api/v1/orders") { with(other) }.andExpect { jsonPath("$.totalElements") { value(0) } }

            // 취소하면 재고가 돌아온다. 두 번은 안 된다.
            mockMvc.post("/api/v1/orders/$orderId/cancel") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
                jsonPath("$.cancelledAt") { exists() }
            }
            assertEquals(10, support.stockOf("모두 베이직 티셔츠", "블랙 / M"))
            mockMvc.post("/api/v1/orders/$orderId/cancel") { with(me) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("취소 상태에서 취소 로 바꿀 수 없습니다.") }
            }
        }

        @Test
        fun `재고보다 많이 주문하면 400 이고 아무것도 바뀌지 않는다`() {
            val addressId = address(me)
            val navyL = support.skuId("모두 베이직 티셔츠", "네이비 / L") // 재고 0
            mockMvc
                .post("/api/v1/orders") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"addressId":$addressId,"items":[{"skuId":$navyL,"quantity":1}]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("재고가 부족합니다: 모두 베이직 티셔츠 (네이비 / L) (남은 수량 0)") }
                }
            mockMvc.get("/api/v1/orders") { with(me) }.andExpect { jsonPath("$.totalElements") { value(0) } }

            mockMvc
                .post("/api/v1/orders") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"addressId":$addressId,"items":[]}"""
                }.andExpect { jsonPath("$.message") { value("items: 주문할 상품이 없습니다.") } }
            mockMvc
                .post("/api/v1/orders") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"addressId":999999,"items":[{"skuId":$navyL,"quantity":1}]}"""
                }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `어드민은 목록을 상태로 거르고 허용된 전이만 한다`() {
            val addressId = address(me)
            val mug = support.skuId("모두 머그컵 세트")
            val orderId =
                JsonPath.read<Int>(
                    mockMvc
                        .post("/api/v1/orders") {
                            with(me)
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"addressId":$addressId,"items":[{"skuId":$mug,"quantity":3}]}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            mockMvc.get("/api-admin/v1/orders") { with(me) }.andExpect { status { isForbidden() } }
            mockMvc
                .get("/api-admin/v1/orders") {
                    param("status", "PAID")
                    with(admin)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.totalElements") { value(1) }
                    jsonPath("$.content[0].userId") { value("11") }
                }

            fun change(status: String) =
                mockMvc.patch("/api-admin/v1/orders/$orderId/status") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"status":"$status"}"""
                }
            change("DELIVERED").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("결제완료 상태에서 배송완료 로 바꿀 수 없습니다.") }
            }
            change("SHIPPING").andExpect { jsonPath("$.status") { value("SHIPPING") } }
            mockMvc.post("/api/v1/orders/$orderId/cancel") { with(me) }.andExpect { status { isBadRequest() } }
            change("DELIVERED").andExpect { jsonPath("$.status") { value("DELIVERED") } }
            mockMvc.get("/api-admin/v1/orders/$orderId") { with(admin) }.andExpect { jsonPath("$.items[0].quantity") { value(3) } }
            assertEquals(42, support.stockOf("모두 머그컵 세트"))

            // 결제완료인 다른 주문을 관리자가 취소하면 재고가 돌아온다
            val second =
                JsonPath.read<Int>(
                    mockMvc
                        .post("/api/v1/orders") {
                            with(me)
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"addressId":$addressId,"items":[{"skuId":$mug,"quantity":2}]}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            assertEquals(40, support.stockOf("모두 머그컵 세트"))
            mockMvc
                .patch("/api-admin/v1/orders/$second/status") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"status":"CANCELLED"}"""
                }.andExpect { jsonPath("$.status") { value("CANCELLED") } }
            assertEquals(42, support.stockOf("모두 머그컵 세트"))
            mockMvc
                .get("/api-admin/v1/orders") {
                    param("status", "CANCELLED")
                    with(admin)
                }.andExpect { jsonPath("$.totalElements") { value(1) } }
        }
    }
