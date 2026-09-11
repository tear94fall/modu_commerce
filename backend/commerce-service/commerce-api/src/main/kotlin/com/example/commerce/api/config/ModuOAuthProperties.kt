package com.example.commerce.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 모두 계정(auth-service) 토큰 검증 설정. 커머스 앱은 aud=modu-commerce 토큰만 쓴다. */
@ConfigurationProperties("modu.oauth")
data class ModuOAuthProperties(
    val issuer: String,
    val jwksUri: String,
    val audience: String = "modu-commerce",
)
