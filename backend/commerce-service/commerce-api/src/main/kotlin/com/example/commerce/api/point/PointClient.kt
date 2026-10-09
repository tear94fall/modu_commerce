package com.example.commerce.api.point

import com.example.commerce.api.config.ModuPointProperties
import com.example.commerce.application.common.logger
import com.example.commerce.application.point.InsufficientPointException
import com.example.commerce.application.point.PointEarnRejectedException
import com.example.commerce.application.point.PointGatewayException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

/**
 * point-service 내부 API 호출. 실패는 종류를 가리지 않고 [PointUnavailableException] 으로 바꿔 503 으로 답한다.
 *
 * 연결·응답 시간 한도는 빈을 만드는 쪽(RemoteClientConfig)이 [builder] 의 요청 팩토리에 건다(modu.point.connect-timeout / read-timeout).
 * 모든 호출은 회로 차단기 [CIRCUIT_BREAKER] 를 지난다. point-service 가 연달아 실패하면 회로가 열려 한동안 부르지 않고 바로
 * [PointUnavailableException] 을 던진다(요청 스레드가 시간 한도만큼 붙잡히지 않는다). 잔액 부족·적립 거절 같은 업무 응답은 실패로 세지 않는다.
 */
class PointClient(
    builder: RestClient.Builder,
    props: ModuPointProperties,
    internalToken: String,
    circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val client =
        builder
            .baseUrl(props.url)
            .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
            .build()

    /** 설정(resilience4j.circuitbreaker.instances.point)이 있으면 그것, 없으면 modu.point.circuit-breaker 값으로 만든다. */
    val circuitBreaker: CircuitBreaker =
        circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER, breakerConfig(props.circuitBreaker))

    fun balance(userId: String): PointBalance =
        call {
            client
                .get()
                .uri("/api-internal/point/{userId}/balance", userId)
                .retrieve()
                .body<PointBalance>()
        }

    fun history(
        userId: String,
        page: Int,
        size: Int,
    ): PointHistoryPage =
        call {
            client
                .get()
                .uri("/api-internal/point/{userId}/history?page={page}&size={size}", userId, page, size)
                .retrieve()
                .body<PointHistoryPage>()
        }

    /** 차감. 잔액 부족(409)은 [InsufficientPointException]. 같은 refId 는 point-service 가 한 번만 차감한다. */
    fun spend(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ): PointChangeResult =
        call {
            try {
                client
                    .post()
                    .uri("/api-internal/point/spend")
                    .body(PointChangeRequest(userId, amount, refId, memo))
                    .retrieve()
                    .body<PointChangeResult>()
            } catch (e: HttpClientErrorException) {
                if (e.statusCode == HttpStatus.CONFLICT) throw InsufficientPointException()
                throw e
            }
        }

    /**
     * 규칙대로 적립. 한도·중복·꺼진 규칙은 point-service 가 applied=false 와 reason 으로 답한다.
     * 규칙이 없으면(404) 장애가 아니라 적립 안 됨(RULE_NOT_FOUND)으로 돌려준다.
     */
    fun earn(
        userId: String,
        ruleCode: String,
        refId: String,
        memo: String?,
    ): PointEarnResponse =
        call {
            try {
                client
                    .post()
                    .uri("/api-internal/point/earn")
                    .body(PointEarnRequest(userId, ruleCode, refId, memo))
                    .retrieve()
                    .body<PointEarnResponse>()
            } catch (e: HttpClientErrorException.NotFound) {
                PointEarnResponse(applied = false, amount = 0, balance = null, reason = "RULE_NOT_FOUND")
            }
        }

    /**
     * 규칙 없는 금액 적립(구매 적립). 같은 refId 는 point-service 가 applied=false, reason=DUPLICATE 로 답한다.
     * 값이 잘못돼 거절되면(400) [PointEarnRejectedException] — 다시 보내도 같으니 재시도하지 않는다.
     */
    fun earnAmount(
        userId: String,
        amount: Long,
        reason: String,
        refId: String,
        memo: String?,
    ): PointEarnResponse =
        call {
            try {
                client
                    .post()
                    .uri("/api-internal/point/earn-amount")
                    .body(PointEarnAmountRequest(userId, amount, reason, refId, memo))
                    .retrieve()
                    .body<PointEarnResponse>()
            } catch (e: HttpClientErrorException.BadRequest) {
                throw PointEarnRejectedException("point-service rejected earn-amount $refId: ${e.responseBodyAsString}")
            }
        }

    /** 환불(차감 되돌리기). 같은 refId 는 한 번만. */
    fun refund(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ): PointChangeResult =
        call {
            client
                .post()
                .uri("/api-internal/point/refund")
                .body(PointChangeRequest(userId, amount, refId, memo))
                .retrieve()
                .body<PointChangeResult>()
        }

    /**
     * 차감 [refId](`order:<번호>`)가 있으면 되돌린다(point-service 는 `refund:` + refId 로 돌려준다 — 취소 환불과 같은 키라 겹치지 않는다).
     * 차감이 없으면 cancelled=false, reason=NO_SPEND.
     */
    fun cancelSpend(
        userId: String,
        refId: String,
        memo: String?,
    ): PointCancelResponse =
        call {
            client
                .post()
                .uri("/api-internal/point/spend/cancel")
                .body(PointCancelRequest(userId, refId, memo))
                .retrieve()
                .body<PointCancelResponse>()
        }

    /** 원장에 있는 거래만 돌려준다(최대 500개). */
    fun refs(refs: List<PointRefKey>): PointRefsResponse =
        call {
            client
                .post()
                .uri("/api-internal/point/refs")
                .body(PointRefsRequest(refs))
                .retrieve()
                .body<PointRefsResponse>()
        }

    private fun <T> call(block: () -> T?): T =
        try {
            circuitBreaker.executeSupplier {
                try {
                    block() ?: throw PointUnavailableException()
                } catch (e: RestClientException) {
                    throw PointUnavailableException(e)
                }
            }
        } catch (e: CallNotPermittedException) {
            logger.warn { "point-service circuit open: ${e.message}" }
            throw PointUnavailableException(e)
        }

    companion object {
        const val INTERNAL_TOKEN_HEADER = "X-Internal-Token"
        const val CIRCUIT_BREAKER = "point"

        /** point-service 장애([PointUnavailableException])만 실패로 센다. 잔액 부족(409)·거절(400)은 서버가 살아 있다는 뜻이다. */
        fun breakerConfig(props: ModuPointProperties.CircuitBreakerProperties): CircuitBreakerConfig =
            CircuitBreakerConfig
                .custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.slidingWindowSize)
                .minimumNumberOfCalls(props.minimumNumberOfCalls)
                .failureRateThreshold(props.failureRateThreshold)
                .waitDurationInOpenState(props.waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(props.permittedCallsInHalfOpenState)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .recordExceptions(PointUnavailableException::class.java)
                .build()
    }
}

/** point-service 를 못 부름(연결 실패·시간 초과·오류 응답·회로 열림). 503 으로 답한다. */
class PointUnavailableException(
    cause: Throwable? = null,
) : RuntimeException(PointGatewayException.MESSAGE, cause)

data class PointBalance(
    val userId: String,
    val balance: Long,
)

/** point-service 의 PointTransactionDto. 커머스는 그대로 웹에 넘긴다. */
data class PointTransaction(
    val id: Long,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val ruleCode: String?,
    val refId: String?,
    val memo: String?,
    val createdDate: String?,
)

/** Spring Data Page 응답 중 웹이 쓰는 필드만. */
data class PointHistoryPage(
    val content: List<PointTransaction>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
)

/** point-service 의 SpendRequestDto. */
data class PointChangeRequest(
    val userId: String,
    val amount: Long,
    val refId: String,
    val memo: String?,
)

/** point-service 의 SpendResultDto. 같은 refId 가 두 번 오면 applied=false. */
data class PointChangeResult(
    val applied: Boolean,
    val amount: Long,
    val balance: Long,
)

/** spend/cancel 요청. refId 는 원래 차감 키(order:<번호>). */
data class PointCancelRequest(
    val userId: String,
    val refId: String,
    val memo: String?,
)

/** spend/cancel 응답. reason: NO_SPEND, ALREADY_REFUNDED, 되돌렸으면 null. */
data class PointCancelResponse(
    val cancelled: Boolean,
    val reason: String? = null,
    val amount: Long = 0,
    val balance: Long = 0,
)

data class PointRefKey(
    val userId: String,
    val refId: String,
)

data class PointRefsRequest(
    val refs: List<PointRefKey>,
)

data class PointRefsResponse(
    val transactions: List<PointRefTransactionDto> = emptyList(),
)

/** refs 응답의 거래 한 건. */
data class PointRefTransactionDto(
    val userId: String,
    val refId: String,
    val type: String,
    val amount: Long,
    val createdDate: String? = null,
)

/** point-service 의 EarnRequestDto. */
data class PointEarnRequest(
    val userId: String,
    val ruleCode: String,
    val refId: String,
    val memo: String?,
)

/** point-service 의 earn-amount 요청(규칙 없이 금액으로 적립). */
data class PointEarnAmountRequest(
    val userId: String,
    val amount: Long,
    val reason: String,
    val refId: String,
    val memo: String?,
)

/** point-service 의 EarnResultDto. reason: RULE_DISABLED, DUPLICATE, TOTAL_LIMIT, DAILY_LIMIT (+ 커머스가 붙이는 RULE_NOT_FOUND). */
data class PointEarnResponse(
    val applied: Boolean,
    val amount: Long,
    val balance: Long?,
    val reason: String? = null,
)
