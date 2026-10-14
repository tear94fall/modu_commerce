package com.example.commerce.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * `/api-internal/` 아래는 다른 서비스(member-service 탈퇴 등)만 부른다. 게이트웨이에 라우트가 없지만 8200 으로 직접 오는
 * 요청이 있으므로 X-Internal-Token(modu.internal-api.token)을 상수 시간 비교로 검사한다.
 * 경로는 Spring 이 핸들러 매핑에 쓰는 것처럼 디코딩·정규화해서 본다(//api-internal, /%61pi-internal 우회 방지).
 * Spring Security 는 이 경로를 permitAll 로 두고(InternalSecurityConfig) 여기서만 막는다.
 */
@Component
class InternalApiFilter(
    @Value("\${modu.internal-api.token}") expectedToken: String,
) : OncePerRequestFilter() {
    private val expected: ByteArray

    init {
        check(StringUtils.hasText(expectedToken)) { "modu.internal-api.token 이 비어 있다. 빈 토큰은 누구나 통과시키므로 기동을 거부한다." }
        expected = expectedToken.toByteArray(StandardCharsets.UTF_8)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = normalizedPath(request)
        return !(path.startsWith(PREFIX) || path == "/api-internal")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val token = request.getHeader(HEADER)
        if (token == null || !MessageDigest.isEqual(token.toByteArray(StandardCharsets.UTF_8), expected)) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""{"message":"internal api token required"}""")
            return
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Internal-Token"
        const val PREFIX = "/api-internal/"
        private val PATH_HELPER = UrlPathHelper()

        fun normalizedPath(request: HttpServletRequest): String =
            StringUtils.cleanPath(PATH_HELPER.getPathWithinApplication(request).replace(Regex("/{2,}"), "/"))
    }
}
