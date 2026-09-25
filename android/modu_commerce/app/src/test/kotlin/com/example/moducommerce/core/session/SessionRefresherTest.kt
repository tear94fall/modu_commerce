package com.example.moducommerce.core.session

import app.cash.turbine.test
import com.example.moducommerce.data.api.AuthApi
import com.example.moducommerce.data.dto.TokenResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SessionRefresherTest {

    private val store = mockk<SessionStore>(relaxed = true)
    private val events = SessionEvents()
    private val authApi = mockk<AuthApi>()
    private val refresher = SessionRefresher(store, events, authApi)

    @Test
    fun `returns the already rotated token without calling the server`() = runTest {
        coEvery { store.accessToken() } returns "new"

        assertEquals("new", refresher.refresh(failedToken = "old"))
        coVerify(exactly = 0) { authApi.token(any()) }
    }

    @Test
    fun `refreshes with the refresh token and stores the pair`() = runTest {
        coEvery { store.accessToken() } returns "old"
        coEvery { store.refreshToken() } returns "r1"
        coEvery { authApi.token(match { it["grant_type"] == "refresh_token" && it["refresh_token"] == "r1" }) } returns
            TokenResponseDto(accessToken = "fresh", refreshToken = "r2")

        assertEquals("fresh", refresher.refresh(failedToken = "old"))
        coVerify { store.saveTokens("fresh", "r2") }
    }

    @Test
    fun `clears the session and announces expiry when the server refuses`() = runTest {
        coEvery { store.accessToken() } returns "old"
        coEvery { store.refreshToken() } returns "r1"
        coEvery { authApi.token(any()) } throws httpError(400)

        events.loggedOut.test {
            assertNull(refresher.refresh(failedToken = "old"))
            assertEquals(true, awaitItem())
        }
        coVerify { store.clearSession() }
        assertFalse(refresher.lastFailureTemporary)
    }

    @Test
    fun `keeps the session when the refresh request cannot reach the server`() = runTest {
        coEvery { store.accessToken() } returns "old"
        coEvery { store.refreshToken() } returns "r1"
        coEvery { authApi.token(any()) } throws IOException("failed to connect")

        events.loggedOut.test {
            assertNull(refresher.refresh(failedToken = "old"))
            expectNoEvents()
        }
        coVerify(exactly = 0) { store.clearSession() }
        assertTrue(refresher.lastFailureTemporary)
    }

    @Test
    fun `keeps the session when the auth server is temporarily down`() = runTest {
        coEvery { store.accessToken() } returns "old"
        coEvery { store.refreshToken() } returns "r1"
        coEvery { authApi.token(any()) } throws httpError(503)

        assertNull(refresher.refresh(failedToken = "old"))
        coVerify(exactly = 0) { store.clearSession() }
        assertTrue(refresher.lastFailureTemporary)
    }

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "{}".toResponseBody()))
}
