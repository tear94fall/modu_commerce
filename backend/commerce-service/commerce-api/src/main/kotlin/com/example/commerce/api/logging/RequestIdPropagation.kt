package com.example.commerce.api.logging

import org.slf4j.MDC
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/** 바깥으로 나가는 호출(point-service, member-service)에 MDC 의 requestId 를 X-Request-Id 로 실어 보낸다. 없으면 붙이지 않는다. */
class RequestIdPropagationInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID)
        if (!requestId.isNullOrBlank() && !request.headers.containsKey(RequestContextFilter.REQUEST_ID_HEADER)) {
            request.headers.set(RequestContextFilter.REQUEST_ID_HEADER, requestId)
        }
        return execution.execute(request, body)
    }
}

/**
 * Boot 가 주입하는 `RestClient.Builder` 전부에 [RequestIdPropagationInterceptor] 를 끼운다.
 * PointClient·RestClientMemberLookup 이 그 빌더로 클라이언트를 만들므로 두 호출 모두 requestId 가 따라간다.
 */
@Configuration
class RequestIdPropagationConfig {
    @Bean
    fun requestIdPropagationCustomizer(): RestClientCustomizer =
        RestClientCustomizer {
            it.requestInterceptor(RequestIdPropagationInterceptor())
        }
}
