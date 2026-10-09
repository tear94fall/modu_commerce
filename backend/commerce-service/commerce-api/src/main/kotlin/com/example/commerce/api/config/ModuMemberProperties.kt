package com.example.commerce.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 모두 챗 member-service. 리뷰 작성자 이름·이메일을 내부 API 로 받아 복사한다. 토큰은 point 와 같은 modu.internal-api.token.
 * 주소(modu.member.url)는 application.yml 이 config-repo 의 modu.services.member-service 로 묶고 MODU_MEMBER_URL 로 덮어쓸 수 있다.
 * 연결·응답 한도는 기본 1초/3초(리뷰 작성이 member-service 를 오래 기다리지 않는다. 실패하면 이름 없이 저장).
 */
@ConfigurationProperties("modu.member")
data class ModuMemberProperties(
    val url: String = "http://member-service:8080",
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val readTimeout: Duration = Duration.ofSeconds(3),
)
