package com.example.commerce.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 모두 챗 백엔드의 point-service 연결 설정. 커머스 토큰(aud=modu-commerce)은 게이트웨이의 포인트 공개 라우트를
 * 못 지나므로, commerce-service 가 같은 도커 네트워크에서 `/api-internal` 을 X-Internal-Token 으로 대신 부른다.
 * 토큰 값은 config-service 공통 설정(modu.internal-api.token)이 내려주며 챗 스택의 INTERNAL_API_TOKEN 과 같아야 한다.
 */
@ConfigurationProperties("modu.point")
data class ModuPointProperties(
    val url: String = "http://point-service:9600",
)
