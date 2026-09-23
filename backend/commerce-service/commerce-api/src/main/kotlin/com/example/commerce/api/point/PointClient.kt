package com.example.commerce.api.point

import com.example.commerce.api.config.ModuPointProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
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
