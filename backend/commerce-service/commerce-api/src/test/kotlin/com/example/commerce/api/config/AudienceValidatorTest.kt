package com.example.commerce.api.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class AudienceValidatorTest {
    private val validator = AudienceValidator("modu-commerce")

    private fun jwt(vararg aud: String): Jwt =
        Jwt
            .withTokenValue("t")
            .header("alg", "RS256")
            .subject("11")
            .audience(aud.toList())
            .build()

    @Test
    fun `aud 에 modu-commerce 가 있으면 통과`() {
        assertFalse(validator.validate(jwt("modu-commerce")).hasErrors())
    }

    @Test
    fun `채팅 앱 토큰(aud=modu-chat)은 거부`() {
        assertTrue(validator.validate(jwt("modu-chat")).hasErrors())
    }
}
