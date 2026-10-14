package com.example.commerce.api.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.logstash.logback.argument.StructuredArguments.kv
import net.logstash.logback.argument.StructuredArguments.v
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청마다 MDC 에 `requestId`(게이트웨이가 준 X-Request-Id 또는 새 값)와 `userId`(X-Auth-User-Id)를 넣고
 * 응답 헤더 X-Request-Id 로 돌려준다. 끝나면 `http.access` 로거에 한 줄(method, path, status, durationMs)을 남긴다.
 * 모든 필터보다 먼저 돌아 Spring Security 가 거절한 요청(401/403)도 같은 requestId 로 남는다.
 * `/actuator/` 아래와 `/v3/api-docs` 는 접속 로그를 남기지 않는다(probe·문서 조회가 로그를 채우지 않게).
 * 빈 이름은 따로 준다(BEAN_NAME). Boot 의 WebMvcAutoConfiguration 이 같은 이름(requestContextFilter)의 빈을 만들어 기본 이름이면 부딪친다.
 */
@Component(RequestContextFilter.BEAN_NAME)
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf(::isValidRequestId) ?: newRequestId()
        val userId = request.getHeader(USER_ID_HEADER)?.takeIf { it.isNotBlank() }

        MDC.put(MDC_REQUEST_ID, requestId)
        if (userId != null) MDC.put(MDC_USER_ID, userId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        val started = System.nanoTime()
        try {
            chain.doFilter(request, response)
        } finally {
            val path = request.requestURI
            if (!isAccessLogExcluded(path)) {
                val durationMs = (System.nanoTime() - started) / 1_000_000
                accessLog.info(
                    // v(): 메시지에는 값만, JSON 에는 필드로. kv() 는 메시지에 key=value 로 찍힌다.
                    "{} {} {} {}ms",
                    v("method", request.method),
                    v("path", path),
                    v("status", response.status),
                    v("durationMs", durationMs),
                    kv("event", "http.access"),
                )
            }
            MDC.remove(MDC_REQUEST_ID)
            if (userId != null) MDC.remove(MDC_USER_ID)
        }
    }

    companion object {
        const val BEAN_NAME = "moduRequestContextFilter"
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val USER_ID_HEADER = "X-Auth-User-Id"
        const val MDC_REQUEST_ID = "requestId"
        const val MDC_USER_ID = "userId"

        private val accessLog = LoggerFactory.getLogger("http.access")
        private val REQUEST_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

        /** 바깥에서 온 값은 그대로 믿지 않는다. 글자·길이가 어긋나면 새로 만든다. */
        fun isValidRequestId(value: String): Boolean = REQUEST_ID_PATTERN.matches(value)

        fun newRequestId(): String =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(16)

        fun isAccessLogExcluded(path: String): Boolean = path.startsWith("/actuator") || path.startsWith("/v3/api-docs")
    }
}
