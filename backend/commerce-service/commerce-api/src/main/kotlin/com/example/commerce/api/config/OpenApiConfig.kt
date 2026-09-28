package com.example.commerce.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** GET /v3/api-docs 의 문서 정보. 시스템 콘솔 "API 문서" 화면이 게이트웨이를 거쳐 읽는다. */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(
        @Value("\${spring.application.name:commerce-service}") name: String,
    ): OpenAPI =
        OpenAPI().info(
            Info()
                .title(name)
                .version("v1")
                .description("모두의 커머스 상품·장바구니·주문·쿠폰·기획전·리뷰와 백오피스 관리를 맡는 서비스입니다."),
        )
}
