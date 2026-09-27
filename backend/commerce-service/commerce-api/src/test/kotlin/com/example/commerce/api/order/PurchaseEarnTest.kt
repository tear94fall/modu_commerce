package com.example.commerce.api.order

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.point.PointEarnRejectedException
import com.example.commerce.application.point.PointEarnResult
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.point.PointGatewayException
import com.example.commerce.application.service.PurchaseEarnService
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import javax.sql.DataSource

/** 배송 완료 구매 적립. point-service 는 포트로 대신한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseEarnTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val purchaseEarnService: PurchaseEarnService,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        @MockitoBean
        private lateinit var pointGateway: PointGateway

        private val jdbc = JdbcTemplate(dataSource)
        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        @BeforeEach
        fun points() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenAnswer {
                PointEarnResult(applied = true, amount = it.getArgument(1))
            }
        }

        private fun tier(code: String) = jdbc.update("update commerce_customers set tier_code = ? where user_id = '11'", code)

        /** 머그컵 세트(18,000원) 한 개 주문. id 와 주문번호. */
        private fun order(usePoints: Long = 0): Pair<Int, String> {
            val addressId =
                JsonPath.read<Int>(
                    mockMvc
                        .post("/api/v1/addresses") {
                            with(me)
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구"}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            val body =
                mockMvc
                    .post("/api/v1/orders") {
                        with(me)
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"addressId":$addressId,"items":[{"skuId":${support.skuId(
                                "모두 머그컵 세트",
                            )},"quantity":1}],"usePoints":$usePoints}"""
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString
            return JsonPath.read<Int>(body, "$.id") to JsonPath.read(body, "$.orderNo")
        }

        private fun status(
            id: Int,
            next: String,
        ) = mockMvc
            .patch("/api-admin/v1/orders/$id/status") {
                with(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"$next"}"""
            }.andExpect { status { isOk() } }

        @Test
        fun `배송 완료되면 지금 등급 적립률로 적립하고 주문에 남긴다`() {
            tier("GOLD")
            val (id, no) = order()
            mockMvc.get("/api/v1/orders/$id") { with(me) }.andExpect {
                jsonPath("$.expectedEarn.points") { value(540) }
                jsonPath("$.expectedEarn.rate") { value(3) }
                jsonPath("$.earn") { doesNotExist() }
            }
            status(id, "SHIPPING")
            mockMvc.get("/api/v1/orders") { with(me) }.andExpect { jsonPath("$.content[0].expectedEarn.points") { value(540) } }
            verify(pointGateway, never()).earnAmount(any(), any(), any(), anyOrNull())

            status(id, "DELIVERED")
            verify(pointGateway).earnAmount(eq("11"), eq(540L), eq("purchase:order:$id"), eq("구매 적립 · 주문 $no (골드 3%)"))
            mockMvc.get("/api/v1/orders/$id") { with(me) }.andExpect {
                jsonPath("$.earn.status") { value("DONE") }
                jsonPath("$.earn.points") { value(540) }
                jsonPath("$.earn.rate") { value(3) }
                jsonPath("$.expectedEarn") { doesNotExist() }
                jsonPath("$.deliveredAt") { exists() }
            }
            mockMvc.get("/api/v1/orders") { with(me) }.andExpect { jsonPath("$.content[0].earn.status") { value("DONE") } }

            // 다시 불려도(같은 주문) 또 적립하지 않는다.
            purchaseEarnService.earn(id.toLong())
            assertThat(purchaseEarnService.retryPending()).isZero()
            verify(pointGateway, times(1)).earnAmount(any(), any(), any(), anyOrNull())
        }

        @Test
        fun `적립은 결제 금액(포인트 뺀 금액) × 적립률을 내림하고 등급마다 다르다`() {
            tier("GOLD")
            val (gold, _) = order(usePoints = 1)
            status(gold, "SHIPPING")
            status(gold, "DELIVERED")
            // 17,999 × 3% = 539.97 → 539
            verify(pointGateway).earnAmount(eq("11"), eq(539L), eq("purchase:order:$gold"), anyOrNull())

            tier("WELCOME")
            val (welcome, _) = order()
            status(welcome, "SHIPPING")
            status(welcome, "DELIVERED")
            verify(pointGateway).earnAmount(eq("11"), eq(180L), eq("purchase:order:$welcome"), anyOrNull())

            tier("VIP")
            val (vip, _) = order()
            mockMvc.get("/api/v1/orders/$vip") { with(me) }.andExpect { jsonPath("$.expectedEarn.points") { value(900) } }
        }

        @Test
        fun `포인트 서버 장애면 PENDING 으로 남고 재시도가 같은 키로 적립한다`() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenThrow(PointGatewayException())
            val (id, _) = order()
            status(id, "SHIPPING")
            status(id, "DELIVERED")
            mockMvc.get("/api/v1/orders/$id") { with(me) }.andExpect {
                jsonPath("$.earn.status") { value("PENDING") }
                jsonPath("$.earn.points") { value(180) }
            }
            // 아직 장애면 그대로 PENDING.
            assertThat(purchaseEarnService.retryPending()).isZero()

            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenReturn(PointEarnResult(applied = true, amount = 180))
            assertThat(purchaseEarnService.retryPending()).isEqualTo(1)
            verify(pointGateway, times(3)).earnAmount(eq("11"), eq(180L), eq("purchase:order:$id"), anyOrNull())
            mockMvc.get("/api/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.earn.status") { value("DONE") } }
            assertThat(purchaseEarnService.retryPending()).isZero()
        }

        @Test
        fun `이미 적립된 키(DUPLICATE)는 DONE, 거절은 FAILED 로 끝나고 재시도하지 않는다`() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull()))
                .thenReturn(PointEarnResult(applied = false, amount = 0, reason = "DUPLICATE"))
            val (dup, _) = order()
            status(dup, "SHIPPING")
            status(dup, "DELIVERED")
            mockMvc.get("/api/v1/orders/$dup") { with(me) }.andExpect { jsonPath("$.earn.status") { value("DONE") } }

            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenThrow(PointEarnRejectedException("bad"))
            val (bad, _) = order()
            status(bad, "SHIPPING")
            status(bad, "DELIVERED")
            mockMvc.get("/api/v1/orders/$bad") { with(me) }.andExpect { jsonPath("$.earn.status") { value("FAILED") } }
            assertThat(purchaseEarnService.retryPending()).isZero()
        }

        @Test
        fun `배송 완료 때 고객이 아니면 적립하지 않는다(NONE)`() {
            val (id, _) = order()
            status(id, "SHIPPING")
            jdbc.update("update commerce_customers set status = 'WITHDRAWN' where user_id = '11'")
            status(id, "DELIVERED")
            verify(pointGateway, never()).earnAmount(any(), any(), any(), anyOrNull())
            val earn = jdbc.queryForMap("select earn_status, earn_points from orders where id = ?", id)
            assertThat(earn["EARN_STATUS"]).isEqualTo("NONE")
            assertThat((earn["EARN_POINTS"] as Number).toLong()).isZero()
        }
    }
