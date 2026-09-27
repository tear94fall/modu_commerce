package com.example.moducommerce.core.push

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** commerce-service 푸시 API. 웹과 같은 출처(WEB_URL 의 /api 프록시)로 부른다. 전부 204. */
interface PushApi {

    @PUT("api/v1/me/push/devices")
    suspend fun registerDevice(@Body body: PushDeviceRequest): Response<Unit>

    /** 내 토큰이 아니어도 204. */
    @DELETE("api/v1/me/push/devices")
    suspend fun unregisterDevice(@Query("token") token: String): Response<Unit>

    /** 사용자당 한 번만 센다. 모르는 id 도 204. */
    @POST("api/v1/push/campaigns/{id}/opened")
    suspend fun reportOpened(@Path("id") campaignId: Long): Response<Unit>
}

data class PushDeviceRequest(val token: String, val platform: String = "ANDROID")
