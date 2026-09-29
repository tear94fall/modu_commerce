package com.example.commerce.api.common

import com.example.commerce.application.service.CustomerQueryService
import com.example.commerce.application.service.CustomerRequiredException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 커머스 가입(약관 동의)한 고객만 부를 수 있는 앱 API. 컨트롤러 클래스나 메서드에 붙인다.
 * 가입하지 않았거나 탈퇴했거나 동의 전(옮겨 온 회원)이면 403 `{message, code:"CUSTOMER_REQUIRED"}`.
 * 상품·카테고리·기획전 조회 같은 둘러보기에는 붙이지 않는다.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomerRequired

/** [CustomerRequired] 검사. 예외는 GlobalExceptionHandler 가 403 으로 바꾼다. */
@Component
class CustomerRequiredInterceptor(
    private val customerQueryService: CustomerQueryService,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod || !required(handler)) return true
        val jwt = SecurityContextHolder.getContext().authentication?.principal as? Jwt ?: throw CustomerRequiredException()
        if (!customerQueryService.isAgreed(jwt.userId())) throw CustomerRequiredException()
        return true
    }

    private fun required(handler: HandlerMethod): Boolean =
        handler.hasMethodAnnotation(CustomerRequired::class.java) ||
            AnnotatedElementUtils.hasAnnotation(handler.beanType, CustomerRequired::class.java)
}

@Configuration
class CustomerRequiredConfig(
    private val customerRequiredInterceptor: CustomerRequiredInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(customerRequiredInterceptor).addPathPatterns("/api-public/**")
    }
}
