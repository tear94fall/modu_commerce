package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceUtilTest {

    @Test
    fun `verifier 는 43자 base64url 이고 매번 다르다`() {
        val a = PkceUtil.generateVerifier()
        val b = PkceUtil.generateVerifier()
        assertEquals(43, a.length)
        assertTrue(a.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertNotEquals(a, b)
    }

    @Test
    fun `challenge 는 RFC 7636 부록 B 예제와 같다`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PkceUtil.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }
}
