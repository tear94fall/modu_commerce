package com.example.commerce.api.point

import com.example.commerce.api.config.ModuPointProperties
import com.example.commerce.api.config.RemoteClientConfig
import com.example.commerce.application.point.InsufficientPointException
import com.sun.net.httpserver.HttpServer
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.time.Duration

class PointClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = PointClient(builder, ModuPointProperties("http://point.test"), "secret-token", CircuitBreakerRegistry.ofDefaults())

    @Test
    fun `내부 토큰을 붙여 잔액을 읽는다`() {
        server
            .expect(requestTo("http://point.test/api-internal/point/u-1/balance"))
            .andExpect(header("X-Internal-Token", "secret-token"))
            .andRespond(withSuccess("""{"userId":"u-1","balance":580}""", MediaType.APPLICATION_JSON))

        assertEquals(PointBalance("u-1", 580), client.balance("u-1"))
        server.verify()
    }

    @Test
    fun `원장은 페이지 그대로 넘긴다`() {
        server
            .expect(requestTo("http://point.test/api-internal/point/u-1/history?page=1&size=5"))
            .andRespond(
                withSuccess(
                    """{"content":[{"id":9,"type":"SPEND","amount":-30,"balanceAfter":80,"ruleCode":null,"refId":"order:1","memo":"주문 할인","createdDate":"2026-09-23T10:00:00"}],
                       "totalElements":6,"totalPages":2,"number":1,"size":5,"first":false,"last":true}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val page = client.history("u-1", 1, 5)

        assertEquals(6, page.totalElements)
        assertEquals(2, page.totalPages)
        assertEquals("SPEND", page.content.single().type)
        assertEquals(-30, page.content.single().amount)
    }

    @Test
    fun `point-service 오류는 PointUnavailableException 이다`() {
        server.expect(requestTo("http://point.test/api-internal/point/u-1/balance")).andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertThrows(PointUnavailableException::class.java) { client.balance("u-1") }
    }

    @Test
    fun `차감은 주문번호를 멱등 키로 보내고 409 는 잔액 부족이다`() {
        server
            .expect(requestTo("http://point.test/api-internal/point/spend"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"userId":"u-1","amount":3000,"refId":"order:20260924-ABC123","memo":"주문 결제 20260924-ABC123"}"""))
            .andRespond(withSuccess("""{"applied":true,"amount":3000,"balance":500}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("http://point.test/api-internal/point/spend"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""{"code":"INSUFFICIENT_POINT"}"""))

        assertEquals(PointChangeResult(true, 3000, 500), client.spend("u-1", 3000, "order:20260924-ABC123", "주문 결제 20260924-ABC123"))
        assertThrows(InsufficientPointException::class.java) { client.spend("u-1", 3000, "order:20260924-ABC123", null) }
        server.verify()
    }

    @Test
    fun `환불은 refund 로 보낸다`() {
        server
            .expect(requestTo("http://point.test/api-internal/point/refund"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"applied":false,"amount":0,"balance":3500}""", MediaType.APPLICATION_JSON))

        assertEquals(PointChangeResult(false, 0, 3500), client.refund("u-1", 3000, "refund:order:1", "주문 취소"))
    }

    @Test
    fun `spend cancel 은 원래 차감 키로 보내고 결과를 그대로 돌려준다`() {
        server
            .expect(requestTo("http://point.test/api-internal/point/spend/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"userId":"u-1","refId":"order:20261009-ABC123","memo":"주문 취소"}"""))
            .andRespond(withSuccess("""{"cancelled":true,"reason":null,"amount":3000,"balance":3500}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("http://point.test/api-internal/point/spend/cancel"))
            .andRespond(withSuccess("""{"cancelled":false,"reason":"NO_SPEND","amount":0,"balance":500}""", MediaType.APPLICATION_JSON))

        assertEquals(PointCancelResponse(true, null, 3000, 3500), client.cancelSpend("u-1", "order:20261009-ABC123", "주문 취소"))
        assertEquals(PointCancelResponse(false, "NO_SPEND", 0, 500), client.cancelSpend("u-1", "order:X", null))
        server.verify()
    }

    @Test
    fun `refs 는 키 목록을 보내고 있는 거래만 받는다`() {
        server
            .expect(requestTo("http://point.test/api-internal/point/refs"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"refs":[{"userId":"u-1","refId":"order:1"},{"userId":"u-1","refId":"refund:order:1"}]}"""))
            .andRespond(
                withSuccess(
                    """{"transactions":[{"userId":"u-1","refId":"order:1","type":"SPEND","amount":-3000,"createdDate":"2026-10-08T10:00:00"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.refs(listOf(PointRefKey("u-1", "order:1"), PointRefKey("u-1", "refund:order:1")))

        assertEquals(listOf(PointRefTransactionDto("u-1", "order:1", "SPEND", -3000, "2026-10-08T10:00:00")), result.transactions)
    }

    @Test
    fun `연달아 실패하면 회로가 열려 부르지 않고 바로 PointUnavailableException 이다`() {
        val props =
            ModuPointProperties(
                "http://point.test",
                circuitBreaker = ModuPointProperties.CircuitBreakerProperties(slidingWindowSize = 4, minimumNumberOfCalls = 4),
            )
        val b = RestClient.builder()
        val s = MockRestServiceServer.bindTo(b).build()
        val breakerClient = PointClient(b, props, "t", CircuitBreakerRegistry.ofDefaults())
        repeat(4) { s.expect(requestTo("http://point.test/api-internal/point/u-1/balance")).andRespond(withStatus(HttpStatus.BAD_GATEWAY)) }

        repeat(4) { assertThrows(PointUnavailableException::class.java) { breakerClient.balance("u-1") } }
        assertEquals(CircuitBreaker.State.OPEN, breakerClient.circuitBreaker.state)
        // 열린 뒤에는 서버를 부르지 않는다(기대한 4번 외의 요청이 오면 MockRestServiceServer 가 실패한다).
        val open = assertThrows(PointUnavailableException::class.java) { breakerClient.balance("u-1") }
        assertInstanceOf(CallNotPermittedException::class.java, open.cause)
        assertEquals("지금은 포인트를 쓸 수 없어요. 포인트 없이 주문하거나 잠시 후 다시 시도해 주세요.", open.message)
        s.verify()
    }

    @Test
    fun `잔액 부족은 회로 실패로 세지 않는다`() {
        val props =
            ModuPointProperties(
                "http://point.test",
                circuitBreaker = ModuPointProperties.CircuitBreakerProperties(slidingWindowSize = 2, minimumNumberOfCalls = 2),
            )
        val b = RestClient.builder()
        val s = MockRestServiceServer.bindTo(b).build()
        val breakerClient = PointClient(b, props, "t", CircuitBreakerRegistry.ofDefaults())
        repeat(3) { s.expect(requestTo("http://point.test/api-internal/point/spend")).andRespond(withStatus(HttpStatus.CONFLICT)) }

        repeat(3) { assertThrows(InsufficientPointException::class.java) { breakerClient.spend("u-1", 1, "order:1", null) } }
        assertEquals(CircuitBreaker.State.CLOSED, breakerClient.circuitBreaker.state)
    }

    @Test
    fun `응답이 읽기 한도보다 늦으면 기다리지 않고 PointUnavailableException 이다`() {
        val slow = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        slow.createContext("/") { exchange ->
            Thread.sleep(2_000)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        slow.start()
        try {
            val props = ModuPointProperties("http://127.0.0.1:${slow.address.port}", readTimeout = Duration.ofMillis(300))
            val timed =
                PointClient(
                    RestClient.builder().requestFactory(RemoteClientConfig.requestFactory(props.connectTimeout, props.readTimeout)),
                    props,
                    "t",
                    CircuitBreakerRegistry.ofDefaults(),
                )
            val started = System.nanoTime()
            assertThrows(PointUnavailableException::class.java) { timed.balance("u-1") }
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(tookMs < 1_500, "read timeout should cut the call short, took ${tookMs}ms")
            assertFalse(timed.circuitBreaker.state == CircuitBreaker.State.OPEN)
        } finally {
            slow.stop(0)
        }
    }
}
