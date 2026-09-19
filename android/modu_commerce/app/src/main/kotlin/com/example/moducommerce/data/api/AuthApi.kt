package com.example.moducommerce.data.api

import com.example.moducommerce.data.dto.TokenResponseDto
import com.example.moducommerce.data.dto.UserInfoDto
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/** 게이트웨이 뒤의 auth-service. 토큰 경로는 인증이 없고 userinfo 는 Bearer 가 필요하다. */
interface AuthApi {

    @FormUrlEncoded
    @POST("auth-service/oauth2/token")
    suspend fun token(@FieldMap form: Map<String, String>): TokenResponseDto

    @FormUrlEncoded
    @POST("auth-service/oauth2/revoke")
    suspend fun revoke(@Field("token") token: String, @Field("client_id") clientId: String)

    @GET("auth-service/userinfo")
    suspend fun userinfo(): UserInfoDto
}
