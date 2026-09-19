package com.example.commerce.api.common

import org.springframework.security.oauth2.jwt.Jwt

/** 앱 체인의 사용자 식별자 = 모두 계정 토큰의 sub(회원 id 문자열). */
fun Jwt.userId(): String = subject
