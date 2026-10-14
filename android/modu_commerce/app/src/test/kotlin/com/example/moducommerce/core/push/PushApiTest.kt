package com.example.moducommerce.core.push

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 푸시 API 는 웹과 같은 출처의 /api-public/v1 를 부른다(nginx·Vite 가 게이트웨이 /commerce-service/api-public 로 넘긴다). */
class PushApiTest {

    private val server = MockWebServer()
    private lateinit var api: PushApi

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PushApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `registers the device under api-public`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.registerDevice(PushDeviceRequest("t1"))

        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api-public/v1/me/push/devices", req.path)
    }

    @Test
    fun `unregisters the device under api-public`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.unregisterDevice("t1")

        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api-public/v1/me/push/devices?token=t1", req.path)
    }

    @Test
    fun `reports a campaign open under api-public`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.reportOpened(7)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api-public/v1/push/campaigns/7/opened", req.path)
    }
}
