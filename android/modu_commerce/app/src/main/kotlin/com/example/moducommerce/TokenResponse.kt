package com.example.moducommerce

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** auth-service /oauth2/token 응답 */
data class TokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0,
) {
    companion object {
        fun parse(json: String): TokenResponse = Gson().fromJson(json, TokenResponse::class.java)
    }
}
