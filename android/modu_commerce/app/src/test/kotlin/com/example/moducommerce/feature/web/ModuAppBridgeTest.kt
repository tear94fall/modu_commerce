package com.example.moducommerce.feature.web

import app.cash.turbine.test
import com.example.moducommerce.core.session.SessionEvents
import com.example.moducommerce.core.session.SessionRefresher
import com.example.moducommerce.core.session.SessionStore
import com.example.moducommerce.data.repository.AuthRepository
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModuAppBridgeTest {
    private val store = mockk<SessionStore>(relaxed = true)
    private val refresher = mockk<SessionRefresher>()
    private val events = SessionEvents()

    private fun bridge(scope: CoroutineScope) = ModuAppBridge(store, refresher, events, mockk<AuthRepository>(relaxed = true), Gson(), scope)

    @Test
    fun `a network failure during refresh does not log the user out`() = runTest {
        coEvery { refresher.refresh("old") } returns null
        every { refresher.lastFailureTemporary } returns true
        val b = bridge(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        events.loggedOut.test {
            assertEquals("", b.refreshAccessToken("old"))
            b.onSessionExpired()
            expectNoEvents()
        }
        coVerify(exactly = 0) { store.clearSession() }
    }

    @Test
    fun `a refused refresh logs the user out`() = runTest {
        coEvery { refresher.refresh("old") } returns null
        every { refresher.lastFailureTemporary } returns false
        val b = bridge(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        events.loggedOut.test {
            assertEquals("", b.refreshAccessToken("old"))
            b.onSessionExpired()
            assertEquals(true, awaitItem())
        }
        coVerify { store.clearSession() }
    }

    @Test
    fun `a 401 after a successful refresh still logs the user out`() = runTest {
        coEvery { refresher.refresh("old") } returns "fresh"
        val b = bridge(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        events.loggedOut.test {
            assertEquals("fresh", b.refreshAccessToken("old"))
            b.onSessionExpired()
            assertEquals(true, awaitItem())
        }
    }
}
