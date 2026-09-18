package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRefresherTest {

    private var access: String? = "old"
    private var refresh: String? = "rt"
    private val renewCalls = mutableListOf<String>()
    private var saved: TokenResponse? = null
    private var renewResult: () -> TokenResponse = { TokenResponse(accessToken = "new", refreshToken = "rt2") }

    private val refresher = TokenRefresher(
        loadAccess = { access },
        loadRefresh = { refresh },
        renew = { renewCalls += it; renewResult() },
        save = { saved = it },
    )

    private fun unauthorized() = ModuAuthClient.AuthException(401, "expired")

    @Test
    fun `성공하면 갱신하지 않는다`() {
        assertEquals("ok:old", refresher.withFreshAccess { "ok:$it" })
        assertTrue(renewCalls.isEmpty())
    }

    @Test
    fun `401 이면 한 번 갱신하고 새 토큰으로 다시 부른다`() {
        val calls = mutableListOf<String>()

        val result = refresher.withFreshAccess { token ->
            calls += token
            if (token == "old") throw unauthorized()
            "ok:$token"
        }

        assertEquals("ok:new", result)
        assertEquals(listOf("old", "new"), calls)
        assertEquals(listOf("rt"), renewCalls)
        assertEquals("new", saved?.accessToken)
    }

    @Test
    fun `refresh 토큰이 없으면 갱신하지 않고 401 로 실패한다`() {
        refresh = null

        val e = assertThrows(ModuAuthClient.AuthException::class.java) { refresher.withFreshAccess<String> { throw unauthorized() } }

        assertEquals(401, e.status)
        assertTrue(renewCalls.isEmpty())
    }

    @Test
    fun `401 이 아닌 오류는 갱신하지 않고 그대로 던진다`() {
        val e = assertThrows(ModuAuthClient.AuthException::class.java) {
            refresher.withFreshAccess<String> { throw ModuAuthClient.AuthException(500, "boom") }
        }

        assertEquals(500, e.status)
        assertTrue(renewCalls.isEmpty())
    }

    @Test
    fun `다시 불러도 401 이면 더 반복하지 않는다`() {
        var calls = 0

        val e = assertThrows(ModuAuthClient.AuthException::class.java) {
            refresher.withFreshAccess<String> { calls++; throw unauthorized() }
        }

        assertEquals(401, e.status)
        assertEquals(2, calls)
        assertEquals(1, renewCalls.size)
    }

    @Test
    fun `갱신이 실패하면 세션 만료(401)로 알린다`() {
        renewResult = { throw ModuAuthClient.AuthException(400, "invalid_grant") }

        val e = assertThrows(ModuAuthClient.AuthException::class.java) { refresher.withFreshAccess<String> { throw unauthorized() } }

        assertTrue(TokenRefresher.isSessionExpired(e))
    }

    @Test
    fun `저장된 토큰이 없으면 부르지 않고 401`() {
        access = null
        var called = false

        val e = assertThrows(ModuAuthClient.AuthException::class.java) { refresher.withFreshAccess { called = true } }

        assertEquals(401, e.status)
        assertEquals(false, called)
    }
}
