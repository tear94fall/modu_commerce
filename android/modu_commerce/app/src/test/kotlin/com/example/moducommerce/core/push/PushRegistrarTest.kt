package com.example.moducommerce.core.push

import com.example.moducommerce.core.session.SessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import retrofit2.Response
import java.io.IOException
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrarTest {
    private val api = mockk<PushApi>()
    private val tokens = mockk<PushTokenSource>()
    private val store = mockk<SessionStore>()

    private fun CoroutineScope.registrar() = PushRegistrar(api, tokens, store, this)

    @Test
    fun `registers the current token only when logged in`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { tokens.currentToken() } returns "fcm-1"
        coEvery { api.registerDevice(any()) } returns Response.success(Unit)

        coEvery { store.accessToken() } returns null
        registrar().registerCurrent()
        coVerify(exactly = 0) { api.registerDevice(any()) }

        coEvery { store.accessToken() } returns "jwt"
        registrar().registerCurrent()
        coVerify(exactly = 1) { api.registerDevice(PushDeviceRequest("fcm-1", "ANDROID")) }
    }

    @Test
    fun `no firebase means no call`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.accessToken() } returns "jwt"
        coEvery { tokens.currentToken() } returns null
        registrar().registerCurrent()
        registrar().unregisterCurrent()
        coVerify(exactly = 0) { api.registerDevice(any()) }
        coVerify(exactly = 0) { api.unregisterDevice(any()) }
    }

    @Test
    fun `unregister failure does not throw`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.accessToken() } returns "jwt"
        coEvery { tokens.currentToken() } returns "fcm-1"
        coEvery { api.unregisterDevice("fcm-1") } throws IOException("down")
        registrar().unregisterCurrent()
        coVerify { api.unregisterDevice("fcm-1") }
    }

    @Test
    fun `new token is registered when logged in`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.accessToken() } returns "jwt"
        coEvery { api.registerDevice(any()) } returns Response.success(Unit)
        registrar().onNewToken("fcm-2")
        coVerify { api.registerDevice(PushDeviceRequest("fcm-2", "ANDROID")) }
    }
}
