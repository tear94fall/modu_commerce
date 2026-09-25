package com.example.commerce.api.point

import com.example.commerce.api.config.ModuPointProperties
import com.example.commerce.application.point.InsufficientPointException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

/** point-service 내부 API 호출. 실패는 종류를 가리지 않고 [PointUnavailableException] 으로 바꿔 503 으로 답한다. */
@Component
class PointClient(
    builder: RestClient.Builder,
    props: ModuPointProperties,
    @Value("\${modu.internal-api.token}") internalToken: String,
) {
    private val client =
        builder
            .baseUrl(props.url)
            .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
            .build()

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

    private fun <T> call(block: () -> T?): T =
        try {
            block() ?: throw PointUnavailableException()
        } catch (e: RestClientException) {
            throw PointUnavailableException(e)
        }

    companion object {
        const val INTERNAL_TOKEN_HEADER = "X-Internal-Token"
    }
}

class PointUnavailableException(
    cause: Throwable? = null,
) : RuntimeException("포인트 서비스에 연결할 수 없습니다.", cause)

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

/** point-service 의 EarnRequestDto. */
data class PointEarnRequest(
    val userId: String,
    val ruleCode: String,
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
