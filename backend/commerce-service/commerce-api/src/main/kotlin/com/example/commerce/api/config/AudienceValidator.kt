package com.example.commerce.api.config

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/** 다른 앱(modu-chat, modu-admin)용 토큰으로 커머스 API 를 부르지 못하게 aud 를 검사한다. */
class AudienceValidator(
    private val audience: String,
) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (token.audience.contains(audience)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "aud 에 $audience 가 없습니다.", null))
        }
}
