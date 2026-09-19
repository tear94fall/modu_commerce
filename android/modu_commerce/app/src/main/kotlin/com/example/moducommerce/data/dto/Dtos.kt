package com.example.moducommerce.data.dto

import com.example.moducommerce.core.model.UserProfile
import com.google.gson.annotations.SerializedName

/** auth-service /oauth2/token 응답. */
data class TokenResponseDto(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0,
)

data class UserInfoDto(
    val name: String? = null,
    val email: String? = null,
    val picture: String? = null,
) {
    fun toModel() = UserProfile(name = name.orEmpty(), email = email.orEmpty(), picture = picture.orEmpty())
}
