package com.example.commerce.api.point

import com.example.commerce.api.config.ModuPointProperties
import com.example.commerce.application.point.InsufficientPointException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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

class PointClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = PointClient(builder, ModuPointProperties("http://point.test"), "secret-token")

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
}
