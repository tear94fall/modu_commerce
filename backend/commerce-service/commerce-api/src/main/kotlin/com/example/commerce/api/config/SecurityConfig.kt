package com.example.commerce.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

/** 모든 API 는 auth-service 가 발급한 RS256 토큰(JWKS 검증, iss/aud 확인)이 있어야 한다. */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    /**
     * 백오피스 전용. 게이트웨이도 같은 검사를 하지만 8200 포트로 직접 오는 요청이 있으므로
     * 여기서 aud=modu-admin 과 roles 의 ROLE_ADMIN 을 다시 본다.
     */
    @Bean
    @Order(1)
    fun adminSecurityFilterChain(
        http: HttpSecurity,
        props: ModuOAuthProperties,
    ): SecurityFilterChain =
        http
            .securityMatcher("/api-admin/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().hasAuthority(ADMIN_ROLE) }
            .oauth2ResourceServer {
                it.jwt { jwt ->
                    jwt.decoder(decoder(props.jwksUri, tokenValidator(props.issuer, props.adminAudience)))
                    jwt.jwtAuthenticationConverter(rolesAuthenticationConverter())
                }
            }.build()

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { it.jwt {} }
            .build()

    /** 앱 체인이 쓰는 기본 디코더(aud=modu-commerce). admin 체인은 자기 디코더를 따로 만든다. */
    @Bean
    fun jwtDecoder(props: ModuOAuthProperties): JwtDecoder = decoder(props.jwksUri, tokenValidator(props.issuer, props.audience))

    companion object {
        const val ADMIN_ROLE = "ROLE_ADMIN"

        fun tokenValidator(
            issuer: String,
            audience: String,
        ): OAuth2TokenValidator<Jwt> =
            DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(issuer), AudienceValidator(audience))

        /** auth-service 는 roles 에 "ROLE_ADMIN" 처럼 접두사까지 넣어 준다. 그대로 권한으로 쓴다. */
        fun rolesAuthenticationConverter(): JwtAuthenticationConverter =
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter(
                    JwtGrantedAuthoritiesConverter().apply {
                        setAuthoritiesClaimName("roles")
                        setAuthorityPrefix("")
                    },
                )
            }

        private fun decoder(
            jwksUri: String,
            validator: OAuth2TokenValidator<Jwt>,
        ): JwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build().apply { setJwtValidator(validator) }
    }
}
