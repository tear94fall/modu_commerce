package com.example.moducommerce.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceAndOAuthTest {

    @Test
    fun `verifier 는 43자 base64url 이고 매번 다르다`() {
        val a = PkceUtil.generateVerifier()
        val b = PkceUtil.generateVerifier()
        assertEquals(43, a.length)
        assertTrue(a.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(a, b)
    }

    @Test
    fun `challenge 는 RFC 7636 부록 B 예시와 같다`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", PkceUtil.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `토큰 폼은 서버에 등록된 client_id 와 grant 문자열을 쓴다`() {
        assertEquals(
            mapOf("grant_type" to "urn:modu:params:oauth:grant-type:sso_code", "client_id" to "modu-commerce", "code" to "c1", "code_verifier" to "v1"),
            OAuthClient.ssoCodeForm("c1", "v1"),
        )
        assertEquals("urn:modu:params:oauth:grant-type:google_id_token", OAuthClient.googleForm("id")["grant_type"])
        assertEquals(mapOf("grant_type" to "refresh_token", "client_id" to "modu-commerce", "refresh_token" to "rt"), OAuthClient.refreshForm("rt"))
    }
}
