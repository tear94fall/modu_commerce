package com.example.commerce.api.logging

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class RequestIdPropagationInterceptorTest {
    private val builder = RestClient.builder().requestInterceptor(RequestIdPropagationInterceptor())
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = builder.baseUrl("http://point.test").build()

    @AfterEach
    fun tearDown() = MDC.clear()

    @Test
    fun `MDC 의 requestId 를 X-Request-Id 로 보낸다`() {
        MDC.put(RequestContextFilter.MDC_REQUEST_ID, "req-123")
        server
            .expect(requestTo("http://point.test/api-internal/point/u-1/balance"))
            .andExpect(header(RequestContextFilter.REQUEST_ID_HEADER, "req-123"))
            .andRespond(withSuccess())

        client
            .get()
            .uri("/api-internal/point/u-1/balance")
            .retrieve()
            .toBodilessEntity()

        server.verify()
    }

    @Test
    fun `requestId 가 없으면 헤더를 붙이지 않는다`() {
        server
            .expect(requestTo("http://point.test/api-internal/point/u-1/balance"))
            .andExpect(headerDoesNotExist(RequestContextFilter.REQUEST_ID_HEADER))
            .andRespond(withSuccess())

        client
            .get()
            .uri("/api-internal/point/u-1/balance")
            .retrieve()
            .toBodilessEntity()

        server.verify()
    }
}
