package com.example.commerce.api.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class SecurityConfigTest {
    private val issuer = "http://auth.test/auth-service"

    private fun jwt(
        aud: String,
        roles: List<String>? = null,
    ): Jwt =
        Jwt
            .withTokenValue("t")
            .header("alg", "RS256")
            .issuer(issuer)
            .subject("admin")
            .audience(listOf(aud))
            .apply { if (roles != null) claim("roles", roles) }
            .build()

    @Test
    fun `admin 검증기는 백오피스 토큰만 통과시킨다`() {
        val validator = SecurityConfig.tokenValidator(issuer, "modu-admin")

        assertFalse(validator.validate(jwt("modu-admin")).hasErrors())
        assertTrue(validator.validate(jwt("modu-commerce")).hasErrors())
    }

    @Test
    fun `roles 클레임을 접두사 없이 권한으로 옮긴다`() {
        val converter = SecurityConfig.rolesAuthenticationConverter()

        val admin = converter.convert(jwt("modu-admin", listOf("ROLE_ADMIN")))!!.authorities.map { it.authority }
        val user = converter.convert(jwt("modu-admin", listOf("ROLE_USER")))!!.authorities.map { it.authority }

        assertTrue(SecurityConfig.ADMIN_ROLE in admin)
        assertFalse(SecurityConfig.ADMIN_ROLE in user)
    }
}
