package com.example.commerce.api.order

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.point.PointCancelResult
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.usecase.order.CancelOrderUseCase
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * 같은 주문을 두 스레드가 동시에 취소한다. 주문 행 잠금(SELECT … FOR UPDATE) 덕에 하나만 취소되고
 * 다른 하나는 잠금을 기다린 뒤 "이미 취소" 를 본다 — 재고는 한 번만 돌아오고 환불도 한 번만 보낸다.
 * H2(MVStore)도 행 잠금을 걸고, 잠금을 얻은 뒤에는 커밋된 최신 행을 읽어 MySQL InnoDB 의 잠금 읽기와 같게 동작한다.
 * 잠금 없이(findByIdAndUserId) 돌리면 두 쪽 다 PAID 를 읽어 재고가 두 번 돌아오고 이 테스트가 실패한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderCancelConcurrencyTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val cancelOrderUseCase: CancelOrderUseCase,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        @MockitoBean
        private lateinit var pointGateway: PointGateway

        private val jdbc = JdbcTemplate(dataSource)
        private val me = jwt().jwt { it.subject("11") }
        private val mug = "모두 머그컵 세트"

        @BeforeEach
        fun setUp() {
            support.reseed()
            whenever(pointGateway.cancelSpend(any(), any(), anyOrNull()))
                .thenReturn(PointCancelResult(cancelled = true, reason = null, amount = 0, balance = 0))
        }

        @AfterEach
        fun tearDown() = support.reseed()

        @RepeatedTest(5)
        fun `두 스레드가 같은 주문을 동시에 취소하면 하나만 성공하고 재고와 환불은 한 번만이다`() {
            val address =
                JsonPath.read<Int>(
                    mockMvc
                        .post("/api-public/v1/addresses") {
                            with(me)
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울","address2":null}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            val body =
                mockMvc
                    .post("/api-public/v1/orders") {
                        with(me)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"addressId":$address,"items":[{"skuId":${support.skuId(mug)},"quantity":2}],"usePoints":1000}"""
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString
            val orderId = JsonPath.read<Int>(body, "$.id").toLong()
            val orderNo = JsonPath.read<String>(body, "$.orderNo")
            val stockAfterOrder = support.stockOf(mug)

            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            val results =
                (1..2)
                    .map {
                        pool.submit<Result<Unit>> {
                            barrier.await(10, TimeUnit.SECONDS)
                            runCatching { cancelOrderUseCase.execute("11", orderId) }.map { }
                        }
                    }.map { it.get(30, TimeUnit.SECONDS) }
            pool.shutdown()

            assertEquals(1, results.count { it.isSuccess }, "exactly one cancel must win: $results")
            val loser = results.single { it.isFailure }.exceptionOrNull()
            assertTrue(loser is IllegalArgumentException, "the other sees the order already cancelled: $loser")
            assertEquals(stockAfterOrder + 2, support.stockOf(mug))
            assertEquals(
                1,
                jdbc.queryForObject(
                    "select count(*) from point_outbox where kind = 'REFUND' and ref_id = ?",
                    Int::class.java,
                    "order:$orderNo",
                ),
            )
            verify(pointGateway, times(1)).cancelSpend(eq("11"), eq("order:$orderNo"), anyOrNull())
        }
    }
