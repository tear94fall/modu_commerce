package com.example.commerce.api.order

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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/**
 * 주문 운영 흐름 — 배송 시작엔 택배사·송장번호, 직원 취소엔 사유가 필수이고, 상태가 바뀔 때마다 처리 이력이 쌓여 상세에 실린다.
 * 여러 건 배송 처리는 건마다 따로라 하나가 실패해도 나머지는 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderOperationsTest
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

        private fun order(quantity: Int = 1): Int {
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
            val mug = support.skuId("모두 머그컵 세트")
            return JsonPath.read(
                mockMvc
                    .post("/api-public/v1/orders") {
                        with(me)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"addressId":$addressId,"items":[{"skuId":$mug,"quantity":$quantity}]}"""
                    }.andReturn()
                    .response.contentAsString,
                "$.id",
            )
        }

        private fun change(
            id: Int,
            body: String,
        ) = mockMvc.patch("/api-admin/v1/orders/$id/status") {
            with(staff)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

        @Test
        fun `배송 시작은 택배사와 송장번호가 있어야 하고, 이력과 함께 상세에 실린다`() {
            val id = order()
            change(id, """{"status":"SHIPPING"}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("택배사를 적어 주세요.") }
            }
            change(id, """{"status":"SHIPPING","carrier":"CJ대한통운"}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("송장번호를 적어 주세요.") }
            }
            change(id, """{"status":"SHIPPING","carrier":" CJ대한통운 ","trackingNo":" 1234-5678 "}""").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("SHIPPING") }
                jsonPath("$.carrier") { value("CJ대한통운") }
                jsonPath("$.trackingNo") { value("1234-5678") }
                jsonPath("$.shippedAt") { exists() }
                jsonPath("$.events.length()") { value(2) }
                jsonPath("$.events[0].fromStatus") { doesNotExist() }
                jsonPath("$.events[0].toStatus") { value("PAID") }
                jsonPath("$.events[0].actorType") { value("CUSTOMER") }
                jsonPath("$.events[0].actorId") { value("11") }
                jsonPath("$.events[1].fromStatus") { value("PAID") }
                jsonPath("$.events[1].toStatus") { value("SHIPPING") }
                jsonPath("$.events[1].actorType") { value("STAFF") }
                jsonPath("$.events[1].actorId") { value("staff-1") }
                jsonPath("$.events[1].trackingNo") { value("1234-5678") }
            }
            // 고객 상세에도 같은 이력·송장이 보인다.
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.trackingNo") { value("1234-5678") }
                jsonPath("$.events.length()") { value(2) }
            }
        }

        @Test
        fun `직원 취소는 사유가 필수이고 사유와 주체가 남는다, 고객 취소는 사유가 선택이다`() {
            val a = order()
            change(a, """{"status":"CANCELLED"}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("취소 사유를 적어 주세요.") }
            }
            change(a, """{"status":"CANCELLED","reason":"재고 부족"}""").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
                jsonPath("$.cancelReason") { value("재고 부족") }
                jsonPath("$.cancelledBy") { value("STAFF") }
                jsonPath("$.events[-1].reason") { value("재고 부족") }
                jsonPath("$.events[-1].actorType") { value("STAFF") }
            }

            val b = order()
            mockMvc
                .post("/api-public/v1/orders/$b/cancel") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"reason":"단순 변심"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.cancelReason") { value("단순 변심") }
                    jsonPath("$.cancelledBy") { value("CUSTOMER") }
                    jsonPath("$.events[-1].actorType") { value("CUSTOMER") }
                }
            val c = order()
            mockMvc.post("/api-public/v1/orders/$c/cancel") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.cancelledBy") { value("CUSTOMER") }
                jsonPath("$.cancelReason") { doesNotExist() }
            }
        }

        @Test
        fun `여러 건 배송 처리는 건마다 따로라 하나가 실패해도 나머지는 된다`() {
            val a = order()
            val b = order()
            mockMvc
                .post("/api-admin/v1/orders/ship") {
                    with(staff)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"orders":[{"orderId":$a,"carrier":"한진","trackingNo":"A1"},{"orderId":$b,"carrier":"한진"},{"orderId":999999,"carrier":"한진","trackingNo":"X"}]}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.results.length()") { value(3) }
                    jsonPath("$.results[0].ok") { value(true) }
                    jsonPath("$.results[0].status") { value("SHIPPING") }
                    jsonPath("$.results[1].ok") { value(false) }
                    jsonPath("$.results[1].message") { value("송장번호를 적어 주세요.") }
                    jsonPath("$.results[2].ok") { value(false) }
                }
            mockMvc.get("/api-admin/v1/orders/$b") { with(staff) }.andExpect { jsonPath("$.status") { value("PAID") } }
        }
    }
