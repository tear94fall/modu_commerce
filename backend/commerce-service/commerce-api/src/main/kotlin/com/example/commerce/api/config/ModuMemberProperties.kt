package com.example.commerce.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 모두 챗 member-service. 리뷰 작성자 이름·이메일을 내부 API 로 받아 복사한다. 토큰은 point 와 같은 modu.internal-api.token. */
@ConfigurationProperties("modu.member")
data class ModuMemberProperties(
    val url: String = "http://member-service:8080",
)
