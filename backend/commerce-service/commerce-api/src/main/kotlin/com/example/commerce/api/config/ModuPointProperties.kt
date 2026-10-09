package com.example.commerce.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 모두 챗 백엔드의 point-service 연결 설정. 커머스 토큰(aud=modu-commerce)은 게이트웨이의 포인트 공개 라우트를
 * 못 지나므로, commerce-service 가 같은 도커 네트워크에서 `/api-internal` 을 X-Internal-Token 으로 대신 부른다.
 * 토큰 값은 config-service 공통 설정(modu.internal-api.token)이 내려주며 챗 스택의 INTERNAL_API_TOKEN 과 같아야 한다.
 * 주소(modu.point.url)는 application.yml 이 config-repo 의 modu.services.point-service 로 묶고 MODU_POINT_URL 로 덮어쓸 수 있다.
 * 아래 기본값은 yml 에 키가 없을 때만 쓰인다(컨테이너 이름).
 *
 * [connectTimeout]/[readTimeout]: 연결·응답 한도(기본 1초/3초). point-service 가 멈춰도 주문 요청이 오래 붙잡히지 않는다.
 * [circuitBreaker]: 연달아 실패하면 회로를 열어 한동안 바로 503 으로 답한다(PointClient).
 * [outbox]: 포인트 아웃박스 릴레이(1분마다)·대사(매일 04:00 KST) 스케줄러. 테스트는 끄고 서비스를 직접 부른다.
 */
@ConfigurationProperties("modu.point")
data class ModuPointProperties(
    val url: String = "http://point-service:9600",
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val readTimeout: Duration = Duration.ofSeconds(3),
    val circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
    val outbox: OutboxProperties = OutboxProperties(),
) {
    /** 최근 [slidingWindowSize] 번 중 [minimumNumberOfCalls] 번 이상 불렀고 실패율이 [failureRateThreshold]% 이상이면 [waitDurationInOpenState] 동안 연다. */
    data class CircuitBreakerProperties(
        val slidingWindowSize: Int = 20,
        val minimumNumberOfCalls: Int = 10,
        val failureRateThreshold: Float = 50f,
        val waitDurationInOpenState: Duration = Duration.ofSeconds(30),
        val permittedCallsInHalfOpenState: Int = 3,
    )

    data class OutboxProperties(
        val relayEnabled: Boolean = true,
        val reconcileEnabled: Boolean = true,
    )
}
