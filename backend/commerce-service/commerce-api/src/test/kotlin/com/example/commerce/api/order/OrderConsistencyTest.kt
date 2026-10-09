package com.example.commerce.api.order

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.api.support.TierClock
import com.example.commerce.application.domain.entity.PointOutbox
import com.example.commerce.application.domain.entity.PointOutboxKind
import com.example.commerce.application.domain.entity.PointOutboxStatus
import com.example.commerce.application.domain.repository.rw.PointOutboxRwRepository
import com.example.commerce.application.point.PointCancelResult
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.point.PointGatewayException
import com.example.commerce.application.point.PointRef
import com.example.commerce.application.point.PointRefTransaction
import com.example.commerce.application.service.PointOutboxService
import com.example.commerce.application.service.PointReconcileService
import com.jayway.jsonpath.JsonPath
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 주문·결제 정합성: Idempotency-Key, 가격 확인, 포인트 아웃박스(SPEND_GUARD·REFUND·릴레이), 대사.
 * point-service 는 포트(PointGateway)를 목으로 대신하고, 시계(TierClock)를 옮겨 릴레이 대기 시간을 흉내 낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TierClock.Config::class)
class OrderConsistencyTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val clock: TierClock,
        private val outboxRepository: PointOutboxRwRepository,
        private val pointOutboxService: PointOutboxService,
        private val pointReconcileService: PointReconcileService,
        private val meterRegistry: MeterRegistry,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        @MockitoBean
        private lateinit var pointGateway: PointGateway

        private val jdbc = JdbcTemplate(dataSource)
        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
        private val mug = "모두 머그컵 세트" // 18,000원

        @BeforeEach
        fun setUp() {
            support.reseed()
            clock.setKst(2026, 10, 9, 12, 0)
            whenever(pointGateway.cancelSpend(any(), any(), anyOrNull())).thenReturn(cancelled())
        }

        @AfterEach
        fun tearDown() = support.reseed()

        private fun cancelled() = PointCancelResult(cancelled = true, reason = null, amount = 0, balance = 0)

        private fun addressId(): Int =
            JsonPath.read(
                mockMvc
                    .post("/api-public/v1/addresses") {
                        with(me)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구","address2":null}"""
                    }.andReturn()
                    .response.contentAsString,
                "$.id",
            )

        private fun order(
            usePoints: Long = 0,
            key: String? = null,
            expected: Long? = null,
            address: Int = addressId(),
        ): ResultActionsDsl =
            mockMvc.post("/api-public/v1/orders") {
                with(me)
                key?.let { header("Idempotency-Key", it) }
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"addressId":$address,"items":[{"skuId":${support.skuId(mug)},"quantity":1}],"usePoints":$usePoints""" +
                    (expected?.let { ""","expectedPaymentAmount":$it""" } ?: "") + "}"
            }

        private fun created(usePoints: Long): Pair<Int, String> {
            val body =
                order(usePoints)
                    .andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString
            return JsonPath.read<Int>(body, "$.id") to JsonPath.read(body, "$.orderNo")
        }

        private fun row(
            kind: PointOutboxKind,
            orderNo: String,
        ): PointOutbox = requireNotNull(outboxRepository.findByKindAndRefId(kind, "order:$orderNo")) { "no $kind row for $orderNo" }

        private fun later(minutes: Long) {
            clock.now = clock.now.plus(Duration.ofMinutes(minutes))
        }

        private fun orderCount(): Int = jdbc.queryForObject("select count(*) from orders", Int::class.java)!!

        // ---- 2-3 Idempotency-Key ----

        @Test
        fun `같은 Idempotency-Key 로 다시 보내면 새로 만들지 않고 처음 주문을 200 으로 돌려준다`() {
            val stock = support.stockOf(mug)
            val address = addressId()
            val first =
                order(1_000, key = "checkout-1", address = address)
                    .andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString
            order(1_000, key = "checkout-1", address = address).andExpect {
                status { isOk() }
                jsonPath("$.id") { value(JsonPath.read<Int>(first, "$.id")) }
                jsonPath("$.orderNo") { value(JsonPath.read<String>(first, "$.orderNo")) }
                jsonPath("$.paymentAmount") { value(17_000) }
            }

            assertEquals(1, orderCount())
            assertEquals(stock - 1, support.stockOf(mug))
            verify(pointGateway, times(1)).spend(any(), any(), any(), anyOrNull())
            // 키가 다르거나 없으면 새 주문이다.
            order(0, key = "checkout-2", address = address).andExpect { status { isCreated() } }
            order(0, address = address).andExpect { status { isCreated() } }
            assertEquals(3, orderCount())
        }

        @Test
        fun `Idempotency-Key 가 64자를 넘거나 비었으면 400`() {
            order(key = "k".repeat(65)).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("Idempotency-Key 는 1~64자여야 합니다.") }
            }
            order(key = " ").andExpect { status { isBadRequest() } }
            order(key = "k".repeat(64)).andExpect { status { isCreated() } }
        }

        // ---- 2-4 가격 확인 ----

        @Test
        fun `화면 금액과 서버 금액이 다르면 409 PRICE_CHANGED 와 새 금액이고 아무것도 바뀌지 않는다`() {
            val stock = support.stockOf(mug)
            order(1_000, expected = 16_000).andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("PRICE_CHANGED") }
                jsonPath("$.message") { value("가격이 바뀌었어요. 결제 금액을 다시 확인해 주세요.") }
                jsonPath("$.paymentAmount") { value(17_000) }
            }
            assertEquals(0, orderCount())
            assertEquals(stock, support.stockOf(mug))
            verify(pointGateway, never()).spend(any(), any(), any(), anyOrNull())
            assertEquals(0, outboxRepository.count())

            order(1_000, expected = 17_000).andExpect { status { isCreated() } }
        }

        // ---- 2-6 SPEND_GUARD ----

        @Test
        fun `주문이 커밋되면 SPEND_GUARD 는 바로 DONE 이고 릴레이는 차감을 되돌리지 않는다`() {
            val (_, orderNo) = created(2_000)
            val guard = row(PointOutboxKind.SPEND_GUARD, orderNo)
            assertEquals(PointOutboxStatus.DONE, guard.status)
            assertEquals(2_000, guard.amount)

            // DONE 표시가 늦은 경우(afterCommit 실패)도 릴레이는 주문이 있는 것을 보고 끝낸다.
            jdbc.update("update point_outbox set status = 'PENDING', done_at = null where id = ?", guard.id)
            later(3)
            assertEquals(1, pointOutboxService.relay())
            assertEquals(PointOutboxStatus.DONE, row(PointOutboxKind.SPEND_GUARD, orderNo).status)
            verify(pointGateway, never()).cancelSpend(any(), any(), anyOrNull())
        }

        @Test
        fun `차감 뒤 주문 트랜잭션이 롤백되면 SPEND_GUARD 가 남고 2분 뒤 릴레이가 차감을 되돌린다`() {
            // point-service 는 차감했지만 응답을 못 받았다(시간 초과) → 주문 트랜잭션 롤백.
            whenever(pointGateway.spend(any(), any(), any(), anyOrNull())).thenThrow(PointGatewayException())
            order(3_000).andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.message") { value("지금은 포인트를 쓸 수 없어요. 포인트 없이 주문하거나 잠시 후 다시 시도해 주세요.") }
            }
            assertEquals(0, orderCount())
            val guard = outboxRepository.findAll().single()
            assertEquals(PointOutboxKind.SPEND_GUARD, guard.kind)
            assertEquals(PointOutboxStatus.PENDING, guard.status)

            assertEquals(0, pointOutboxService.relay()) // 아직 2분이 안 됐다
            verify(pointGateway, never()).cancelSpend(any(), any(), anyOrNull())

            later(3)
            assertEquals(1, pointOutboxService.relay())
            verify(pointGateway).cancelSpend(eq("11"), eq(guard.refId), anyOrNull())
            assertEquals(PointOutboxStatus.DONE, outboxRepository.findById(requireNotNull(guard.id)).get().status)
        }

        @Test
        fun `포인트를 안 쓰는 주문은 point-service 가 죽어도 된다`() {
            whenever(pointGateway.spend(any(), any(), any(), anyOrNull())).thenThrow(PointGatewayException())
            order(0).andExpect { status { isCreated() } }
            assertEquals(0, outboxRepository.count())
        }

        // ---- 2-6 REFUND ----

        @Test
        fun `point-service 가 죽어도 취소는 커밋되고 환불은 PENDING 으로 남았다가 릴레이가 보낸다`() {
            val stock = support.stockOf(mug)
            val (id, orderNo) = created(3_000)
            whenever(pointGateway.cancelSpend(any(), any(), anyOrNull())).thenThrow(PointGatewayException())

            mockMvc.post("/api-public/v1/orders/$id/cancel") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
                jsonPath("$.pointRefundStatus") { value("PENDING") }
            }
            assertEquals(stock, support.stockOf(mug))
            val refund = row(PointOutboxKind.REFUND, orderNo)
            assertEquals(PointOutboxStatus.PENDING, refund.status)
            assertEquals(1, refund.attempts) // 커밋 뒤 바로 한 번 보냈다가 실패
            assertEquals(3_000, refund.amount)
            assertEquals(1.0, meterRegistry.get("commerce.point.outbox.pending").gauge().value())
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.pointRefundStatus") { value("PENDING") } }
            mockMvc.get("/api-admin/v1/orders/$id") { with(admin) }.andExpect { jsonPath("$.pointRefundStatus") { value("PENDING") } }

            // 살아난 뒤 1분이 지나면 릴레이가 보낸다.
            whenever(pointGateway.cancelSpend(any(), any(), anyOrNull())).thenReturn(cancelled())
            assertEquals(0, pointOutboxService.relay())
            later(1)
            assertEquals(1, pointOutboxService.relay())
            verify(pointGateway, times(2)).cancelSpend(eq("11"), eq("order:$orderNo"), eq("주문 취소 $orderNo"))
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.pointRefundStatus") { value("DONE") } }
            assertEquals(0.0, meterRegistry.get("commerce.point.outbox.pending").gauge().value())
        }

        @Test
        fun `릴레이는 실패할 때마다 1분 2분 4분 기다리고 20번 실패하면 FAILED 다`() {
            val (id, orderNo) = created(1_000)
            whenever(pointGateway.cancelSpend(any(), any(), anyOrNull())).thenThrow(PointGatewayException())
            mockMvc.post("/api-public/v1/orders/$id/cancel") { with(me) }.andExpect { status { isOk() } }

            val waits = mutableListOf<Long>()
            var previous = row(PointOutboxKind.REFUND, orderNo)
            while (previous.status == PointOutboxStatus.PENDING) {
                clock.now = previous.nextAttemptAt.toInstant(java.time.ZoneOffset.UTC)
                pointOutboxService.relay()
                val next = row(PointOutboxKind.REFUND, orderNo)
                if (next.status ==
                    PointOutboxStatus.PENDING
                ) {
                    waits += Duration.between(previous.nextAttemptAt, next.nextAttemptAt).toMinutes()
                }
                previous = next
            }
            assertEquals(listOf(2L, 4L, 8L, 16L, 32L, 60L), waits.take(6)) // 첫 1분은 취소 직후 시도에서
            assertEquals(PointOutboxStatus.FAILED, previous.status)
            assertEquals(20, previous.attempts)
            assertNotNull(previous.lastError)
            mockMvc.get("/api-public/v1/orders/$id") { with(me) }.andExpect { jsonPath("$.pointRefundStatus") { value("FAILED") } }
        }

        @Test
        fun `환불에 NO_SPEND 가 오면 이상으로 보고 FAILED 로 둔다`() {
            val (id, orderNo) = created(1_000)
            whenever(pointGateway.cancelSpend(any(), any(), anyOrNull()))
                .thenReturn(PointCancelResult(cancelled = false, reason = PointCancelResult.NO_SPEND, amount = 0, balance = 0))
            mockMvc
                .patch("/api-admin/v1/orders/$id/status") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"status":"CANCELLED"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.pointRefundStatus") { value("FAILED") }
                }
            assertEquals("NO_SPEND: point-service has no spend order:$orderNo", row(PointOutboxKind.REFUND, orderNo).lastError)
        }

        @Test
        fun `포인트를 안 쓴 주문의 환불 상태는 NONE 이다`() {
            val (id, _) = created(0)
            mockMvc.post("/api-public/v1/orders/$id/cancel") { with(me) }.andExpect { jsonPath("$.pointRefundStatus") { value("NONE") } }
            assertEquals(0, outboxRepository.count())
        }

        // ---- 2-6 대사 ----

        @Test
        fun `대사는 차감 없음 금액 다름 환불 없음을 찾고 환불 없는 취소에는 REFUND 를 다시 만든다`() {
            val (_, missingSpend) = created(1_000)
            val (cancelId, cancelledNo) = created(2_000)
            val (_, mismatchNo) = created(3_000)
            mockMvc.post("/api-public/v1/orders/$cancelId/cancel") { with(me) }.andExpect { status { isOk() } }
            // 아웃박스 이전에 취소된 것처럼 REFUND 행을 지운다(환불이 원장에 없다).
            jdbc.update("delete from point_outbox where kind = 'REFUND'")
            // 모두 2026-10-08(KST) 오전에 만들고 취소했다.
            val at = Timestamp.valueOf(TierClock.utc(2026, 10, 8, 10, 0))
            jdbc.update("update orders set created_at = ?", at)
            jdbc.update("update orders set cancelled_at = ? where id = ?", at, cancelId)
            clock.setKst(2026, 10, 9, 4, 0)
            whenever(pointGateway.findTransactions(any())).thenAnswer { inv ->
                val refs = inv.getArgument<List<PointRef>>(0).map { it.refId }.toSet()
                listOf(
                    PointRefTransaction("11", "order:$cancelledNo", "SPEND", -2_000, null),
                    PointRefTransaction("11", "order:$mismatchNo", "SPEND", -2_500, null),
                ).filter { it.refId in refs }
            }
            val before = listOf("spend_missing", "refund_missing", "amount_mismatch").associateWith { anomalies(it) }

            val report = pointReconcileService.reconcile(LocalDate.of(2026, 10, 8))

            assertEquals(3, report.checked)
            assertEquals(mapOf("spend_missing" to 1, "amount_mismatch" to 1, "refund_missing" to 1), report.anomalies)
            assertEquals(1, report.refundsQueued)
            before.forEach { (type, count) -> assertEquals(count + 1, anomalies(type), type) }
            val refs = argumentCaptor<List<PointRef>>()
            verify(pointGateway).findTransactions(refs.capture())
            assertTrue(PointRef("11", "refund:order:$cancelledNo") in refs.firstValue)
            assertTrue(PointRef("11", "order:$missingSpend") in refs.firstValue)
            val queued = row(PointOutboxKind.REFUND, cancelledNo)
            assertEquals(PointOutboxStatus.PENDING, queued.status)
            assertEquals(2_000, queued.amount)
            assertEquals(TierClock.utc(2026, 10, 9, 4, 0), queued.nextAttemptAt)

            // 다른 날은 대상이 아니다.
            assertEquals(0, pointReconcileService.reconcile(LocalDate.of(2026, 10, 7)).checked)
        }

        private fun anomalies(type: String): Double =
            meterRegistry
                .find("commerce.point.reconcile.anomalies")
                .tag("type", type)
                .counter()
                ?.count() ?: 0.0
    }
