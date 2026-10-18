package com.example.commerce.api.order

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.api.support.TierClock
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.PointOutbox
import com.example.commerce.application.domain.entity.PointOutboxKind
import com.example.commerce.application.domain.entity.PointOutboxStatus
import com.example.commerce.application.domain.repository.rw.PointOutboxRwRepository
import com.example.commerce.application.point.PointEarnRejectedException
import com.example.commerce.application.point.PointEarnResult
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.point.PointGatewayException
import com.example.commerce.application.service.OrderCommandService
import com.example.commerce.application.service.PointOutboxService
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import javax.sql.DataSource

/**
 * 배송 완료 구매 적립 — 포인트 아웃박스(EARN). point-service 는 포트로 대신하고, 시계(TierClock)를 옮겨 릴레이 대기 시간을 흉내 낸다.
 * 설계: docs/superpowers/specs/2026-10-18-purchase-earn-outbox-design.md
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TierClock.Config::class)
class PurchaseEarnTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val clock: TierClock,
        private val outboxRepository: PointOutboxRwRepository,
        private val pointOutboxService: PointOutboxService,
        private val orderCommandService: OrderCommandService,
        @Qualifier("rwTransactionManager") private val rwTransactionManager: PlatformTransactionManager,
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
            clock.setKst(2026, 10, 18, 12, 0)
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
                        .post("/api-public/v1/addresses") {
                            with(me)
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구"}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            val body =
                mockMvc
                    .post("/api-public/v1/orders") {
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

        private fun delivered(id: Int) {
            status(id, "SHIPPING")
            status(id, "DELIVERED")
        }

        private fun earnRow(id: Int): PointOutbox? = outboxRepository.findByKindAndRefId(PointOutboxKind.EARN, "purchase:order:$id")

        private fun later(minutes: Long) {
            clock.now = clock.now.plus(Duration.ofMinutes(minutes))
        }

        private fun earnColumns(id: Int): Map<String, Any?> = jdbc.queryForMap("select earn_points, earn_rate from orders where id = ?", id)

        @Test
        fun `적립이 적용되지 않았다는 답(DUPLICATE 아님)은 FAILED 로 두고 다시 보내지 않는다`() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull()))
                .thenReturn(PointEarnResult(applied = false, amount = 0, reason = "RULE_DISABLED"))
            val (id, _) = order()
            delivered(id)
            val row = requireNotNull(earnRow(id))
            assertThat(row.status).isEqualTo(PointOutboxStatus.FAILED)
            assertThat(row.lastError).startsWith("NOT_APPLIED: RULE_DISABLED")
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.earn.status") { value("FAILED") } }
            later(60)
            assertThat(pointOutboxService.relay()).isZero()
            verify(pointGateway, times(1)).earnAmount(any(), any(), any(), anyOrNull())
        }

        @Test
        fun `배송 완료되면 그 시점 등급 적립률로 금액을 정해 EARN 행을 남기고 커밋 뒤 바로 적립한다`() {
            tier("GOLD")
            val (id, no) = order()
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect {
                jsonPath("$.expectedEarn.points") { value(540) }
                jsonPath("$.expectedEarn.rate") { value(3) }
                jsonPath("$.earn") { doesNotExist() }
            }
            status(id, "SHIPPING")
            mockMvc.get("/api-public/v1/orders") { with(me) }.andExpect { jsonPath("$.content[0].expectedEarn.points") { value(540) } }
            verify(pointGateway, never()).earnAmount(any(), any(), any(), anyOrNull())
            assertThat(earnRow(id)).isNull()

            status(id, "DELIVERED").andExpect { jsonPath("$.earn.status") { value("DONE") } }
            verify(pointGateway).earnAmount(eq("11"), eq(540L), eq("purchase:order:$id"), eq("구매 적립 · 주문 $no"))
            val row = requireNotNull(earnRow(id))
            assertThat(row.status).isEqualTo(PointOutboxStatus.DONE)
            assertThat(row.amount).isEqualTo(540)
            assertThat(row.orderNo).isEqualTo(no)
            assertThat(row.attempts).isZero()
            // 주문에는 금액·적립률만 남는다(진행 상태는 EARN 행).
            val cols = earnColumns(id)
            assertThat((cols["EARN_POINTS"] as Number).toLong()).isEqualTo(540)
            assertThat((cols["EARN_RATE"] as Number).toInt()).isEqualTo(3)

            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect {
                jsonPath("$.earn.status") { value("DONE") }
                jsonPath("$.earn.points") { value(540) }
                jsonPath("$.earn.rate") { value(3) }
                jsonPath("$.expectedEarn") { doesNotExist() }
                jsonPath("$.deliveredAt") { exists() }
            }
            mockMvc.get("/api-public/v1/orders") { with(me) }.andExpect { jsonPath("$.content[0].earn.status") { value("DONE") } }

            // 릴레이가 돌아도 DONE 행은 다시 보내지 않는다.
            assertThat(pointOutboxService.relay()).isZero()
            verify(pointGateway, times(1)).earnAmount(any(), any(), any(), anyOrNull())
        }

        @Test
        fun `적립은 결제 금액(포인트 뺀 금액) × 적립률을 내림하고 등급마다 다르다`() {
            tier("GOLD")
            val (gold, _) = order(usePoints = 1)
            delivered(gold)
            // 17,999 × 3% = 539.97 → 539
            verify(pointGateway).earnAmount(eq("11"), eq(539L), eq("purchase:order:$gold"), anyOrNull())

            tier("WELCOME")
            val (welcome, _) = order()
            delivered(welcome)
            verify(pointGateway).earnAmount(eq("11"), eq(180L), eq("purchase:order:$welcome"), anyOrNull())

            tier("VIP")
            val (vip, _) = order()
            mockMvc.get("/api-public/v1/orders/$vip") { with(me) }.andExpect { jsonPath("$.expectedEarn.points") { value(900) } }
        }

        @Test
        fun `point-service 가 죽어도 배송 완료는 커밋되고 적립은 PENDING 으로 남았다가 릴레이가 같은 키로 보낸다`() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenThrow(PointGatewayException())
            val (id, _) = order()
            status(id, "SHIPPING")
            status(id, "DELIVERED") // 200
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect {
                jsonPath("$.status") { value("DELIVERED") }
                jsonPath("$.earn.status") { value("PENDING") }
                jsonPath("$.earn.points") { value(180) }
            }
            val row = requireNotNull(earnRow(id))
            assertThat(row.status).isEqualTo(PointOutboxStatus.PENDING)
            assertThat(row.attempts).isEqualTo(1) // 커밋 뒤 바로 한 번 보냈다가 실패
            assertThat(row.lastError).contains("PointGatewayException")

            // 아직 1분이 안 됐다 → 릴레이는 건드리지 않는다. 1분 뒤 아직 장애면 attempts 2.
            assertThat(pointOutboxService.relay()).isZero()
            later(1)
            assertThat(pointOutboxService.relay()).isZero()
            assertThat(requireNotNull(earnRow(id)).attempts).isEqualTo(2)

            // 살아난 뒤 다음 차례(2분)에 같은 키로 보내 DONE.
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenReturn(PointEarnResult(applied = true, amount = 180))
            later(2)
            assertThat(pointOutboxService.relay()).isEqualTo(1)
            verify(pointGateway, times(3)).earnAmount(eq("11"), eq(180L), eq("purchase:order:$id"), anyOrNull())
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.earn.status") { value("DONE") } }
            assertThat(pointOutboxService.relay()).isZero()
        }

        @Test
        fun `DUPLICATE 는 DONE 이고 거절(4xx)은 FAILED 로 끝나 릴레이가 다시 보내지 않는다`() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull()))
                .thenReturn(PointEarnResult(applied = false, amount = 0, reason = "DUPLICATE"))
            val (dup, _) = order()
            delivered(dup)
            assertThat(requireNotNull(earnRow(dup)).status).isEqualTo(PointOutboxStatus.DONE)
            mockMvc.get("/api-public/v1/orders/$dup") { with(me) }.andExpect { jsonPath("$.earn.status") { value("DONE") } }

            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenThrow(PointEarnRejectedException("bad"))
            val (bad, _) = order()
            delivered(bad)
            val failed = requireNotNull(earnRow(bad))
            assertThat(failed.status).isEqualTo(PointOutboxStatus.FAILED)
            assertThat(failed.lastError).contains("bad")
            mockMvc.get("/api-public/v1/orders/$bad") { with(me) }.andExpect { jsonPath("$.earn.status") { value("FAILED") } }

            later(60)
            assertThat(pointOutboxService.relay()).isZero()
            verify(pointGateway, times(2)).earnAmount(any(), any(), any(), anyOrNull())
        }

        @Test
        fun `배송 완료 때 고객이 아니면 적립하지 않는다(NONE, 행 없음)`() {
            val (id, _) = order()
            status(id, "SHIPPING")
            jdbc.update("update commerce_customers set status = 'WITHDRAWN' where user_id = '11'")
            status(id, "DELIVERED")
            verify(pointGateway, never()).earnAmount(any(), any(), any(), anyOrNull())
            assertThat(earnRow(id)).isNull()
            assertThat((earnColumns(id)["EARN_POINTS"] as Number).toLong()).isZero()
            mockMvc.get("/api-admin/v1/orders/$id") { with(admin) }.andExpect {
                jsonPath("$.earn.status") { value("NONE") }
                jsonPath("$.earn.points") { value(0) }
            }
        }

        @Test
        fun `배송 완료 트랜잭션이 롤백되면 적립 행도 금액도 남지 않는다`() {
            val (id, _) = order()
            status(id, "SHIPPING")

            val tx = TransactionTemplate(rwTransactionManager)
            assertThrows<IllegalStateException> {
                tx.executeWithoutResult {
                    orderCommandService.changeStatus(id.toLong(), OrderStatus.DELIVERED)
                    throw IllegalStateException("커밋 직전에 터진다")
                }
            }

            verify(pointGateway, never()).earnAmount(any(), any(), any(), anyOrNull())
            assertThat(earnRow(id)).isNull()
            assertThat(earnColumns(id)["EARN_POINTS"]).isNull()
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect {
                jsonPath("$.status") { value("SHIPPING") }
                jsonPath("$.earn") { doesNotExist() }
            }
        }

        @Test
        fun `적립액이 있는데 EARN 행이 없으면(있을 수 없는 상태) PENDING 으로 보여 준다`() {
            val (id, _) = order()
            delivered(id)
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.earn.status") { value("DONE") } }

            // 행은 배송 완료와 같은 트랜잭션에 생기므로 없을 수 없다. 혹시 지워졌다면 대사(ensureEarn)가 다시 만들 때까지 PENDING.
            jdbc.update("delete from point_outbox where kind = 'EARN' and ref_id = ?", "purchase:order:$id")
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.earn.status") { value("PENDING") } }
            mockMvc.get("/api-admin/v1/orders/$id") { with(admin) }.andExpect { jsonPath("$.earn.status") { value("PENDING") } }
        }

        @Test
        fun `목록은 주문마다 EARN 행의 상태를 붙인다`() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenThrow(PointGatewayException())
            val (a, _) = order()
            delivered(a) // PENDING
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenReturn(PointEarnResult(applied = true, amount = 180))
            val (b, _) = order()
            delivered(b) // DONE

            // 최신순: b 가 먼저.
            mockMvc.get("/api-public/v1/orders") { with(me) }.andExpect {
                jsonPath("$.content[0].id") { value(b) }
                jsonPath("$.content[0].earn.status") { value("DONE") }
                jsonPath("$.content[1].id") { value(a) }
                jsonPath("$.content[1].earn.status") { value("PENDING") }
            }
            mockMvc.get("/api-admin/v1/orders") { with(admin) }.andExpect {
                jsonPath("$.content[0].earn.status") { value("DONE") }
                jsonPath("$.content[1].earn.status") { value("PENDING") }
            }
        }
    }
