package com.example.commerce.api.member

import com.example.commerce.api.config.ModuMemberProperties
import com.example.commerce.api.point.PointClient
import com.example.commerce.application.common.logger
import com.example.commerce.application.member.MemberLookup
import com.example.commerce.application.member.MemberProfile
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/** member-service `/api-internal/member/members?userIds=` 로 한 명을 찾는다. 못 찾거나 실패하면 null(리뷰는 이름 없이 저장된다). */
@Component
class RestClientMemberLookup(
    builder: RestClient.Builder,
    props: ModuMemberProperties,
    @Value("\${modu.internal-api.token}") internalToken: String,
) : MemberLookup {
    private val client =
        builder
            .baseUrl(props.url)
            .defaultHeader(PointClient.INTERNAL_TOKEN_HEADER, internalToken)
            .build()

    override fun find(userId: String): MemberProfile? =
        runCatching {
            client
                .get()
                .uri("/api-internal/member/members?userIds={userId}", userId)
                .retrieve()
                .body<List<MemberSummary>>()
                ?.firstOrNull { it.userId == userId }
                ?.let { MemberProfile(userId, it.username, it.email) }
        }.onFailure { logger.warn { "member lookup failed for $userId: ${it.message}" } }
            .getOrNull()

    /** member-service 응답 중 쓰는 필드만. 모르는 필드는 무시된다. */
    data class MemberSummary(
        val userId: String? = null,
        val username: String? = null,
        val email: String? = null,
    )
}
