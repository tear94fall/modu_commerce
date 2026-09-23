package com.example.commerce.api.order

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.point.InsufficientPointException
import com.example.commerce.application.point.PointGateway
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/** 결제에 포인트를 쓰는 흐름. point-service 는 포트로 대신한다. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderPointTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
    ) {
        @MockitoBean
        private lateinit var pointGateway: PointGateway

        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        private fun addressId(): Int =
            JsonPath.read(
                mockMvc
                    .post("/api/v1/addresses") {
                        with(me)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구","address2":null}"""
                    }.andReturn()
                    .response.contentAsString,
                "$.id",
            )

        private fun order(usePoints: Long) =
            mockMvc.post("/api/v1/orders") {
                with(me)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"addressId":${addressId()},"items":[{"skuId":${support.skuId("모두 머그컵 세트")},"quantity":1}],"usePoints":$usePoints}"""
            }

        @Test
        fun `포인트를 쓰면 주문번호를 멱등 키로 차감하고 취소하면 돌려준다`() {
            val body =
                order(3_000)
                    .andExpect {
                        status { isCreated() }
                        jsonPath("$.totalAmount") { value(18_000) }
                        jsonPath("$.pointAmount") { value(3_000) }
                        jsonPath("$.paymentAmount") { value(15_000) }
                    }.andReturn()
                    .response.contentAsString
            val orderId = JsonPath.read<Int>(body, "$.id")
            val orderNo = JsonPath.read<String>(body, "$.orderNo")
            verify(pointGateway).spend(eq("11"), eq(3_000L), eq("order:$orderNo"), eq("주문 결제 $orderNo"))

            mockMvc.get("/api/v1/orders") { with(me) }.andExpect {
                jsonPath("$.content[0].pointAmount") { value(3_000) }
                jsonPath("$.content[0].paymentAmount") { value(15_000) }
            }

            mockMvc.post("/api/v1/orders/$orderId/cancel") { with(me) }.andExpect { status { isOk() } }
            verify(pointGateway).refund(eq("11"), eq(3_000L), eq("refund:order:$orderNo"), eq("주문 취소 $orderNo"))
        }

        @Test
        fun `관리자 취소도 포인트를 돌려준다`() {
            val body = order(500).andReturn().response.contentAsString
            val orderId = JsonPath.read<Int>(body, "$.id")
            val orderNo = JsonPath.read<String>(body, "$.orderNo")

            mockMvc
                .patch("/api-admin/v1/orders/$orderId/status") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"status":"CANCELLED"}"""
                }.andExpect { status { isOk() } }
            verify(pointGateway).refund(eq("11"), eq(500L), eq("refund:order:$orderNo"), anyOrNull())
        }

        @Test
        fun `포인트를 안 쓰면 포인트 서버를 부르지 않는다`() {
            order(0).andExpect {
                status { isCreated() }
                jsonPath("$.pointAmount") { value(0) }
                jsonPath("$.paymentAmount") { value(18_000) }
            }
            verify(pointGateway, never()).spend(any(), any(), any(), anyOrNull())
        }

        @Test
        fun `상품 금액을 넘는 포인트와 잔액 부족은 400 이고 주문이 남지 않는다`() {
            val stockBefore = support.stockOf("모두 머그컵 세트")
            order(18_001).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("포인트는 상품 금액(18000원)까지만 쓸 수 있습니다.") }
            }
            whenever(pointGateway.spend(any(), any(), any(), anyOrNull())).thenThrow(InsufficientPointException())
            order(1_000).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("포인트가 부족합니다. 잔액을 확인해 주세요.") }
            }
            mockMvc.get("/api/v1/orders") { with(me) }.andExpect { jsonPath("$.totalElements") { value(0) } }
            org.junit.jupiter.api.Assertions
                .assertEquals(support.stockOf("모두 머그컵 세트"), support.stockOf("모두 머그컵 세트"))
        }
    }
