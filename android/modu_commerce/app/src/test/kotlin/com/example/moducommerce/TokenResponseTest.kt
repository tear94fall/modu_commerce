package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenResponseTest {

    @Test
    fun `스네이크 케이스 응답을 파싱한다`() {
        val t = TokenResponse.parse("""{"access_token":"at","refresh_token":"rt","expires_in":3600,"token_type":"Bearer"}""")
        assertEquals("at", t.accessToken)
        assertEquals("rt", t.refreshToken)
        assertEquals(3600L, t.expiresIn)
    }

    @Test
    fun `없는 필드는 null 이다`() {
        val t = TokenResponse.parse("""{"access_token":"at"}""")
        assertNull(t.refreshToken)
        assertEquals(0L, t.expiresIn)
    }
}
