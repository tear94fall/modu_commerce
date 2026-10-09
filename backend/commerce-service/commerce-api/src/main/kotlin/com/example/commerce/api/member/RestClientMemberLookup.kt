package com.example.commerce.api.member

import com.example.commerce.api.config.ModuMemberProperties
import com.example.commerce.api.point.PointClient
import com.example.commerce.application.common.logger
import com.example.commerce.application.member.MemberLookup
import com.example.commerce.application.member.MemberProfile
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * member-service `/api-internal/member/members?userIds=` 로 한 명을 찾는다. 못 찾거나 실패하면 null(리뷰는 이름 없이 저장된다).
 * 빈은 RemoteClientConfig 가 연결·응답 한도(modu.member.connect-timeout / read-timeout)를 건 빌더로 만든다.
 */
class RestClientMemberLookup(
    builder: RestClient.Builder,
    props: ModuMemberProperties,
    internalToken: String,
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

    /** 한 번에 여러 명(백오피스 고객 목록 한 페이지). 실패하면 빈 맵(이름·이메일 없이 보인다). */
    override fun findAll(userIds: Collection<String>): Map<String, MemberProfile> {
        val ids = userIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return runCatching {
            client
                .get()
                .uri("/api-internal/member/members?userIds={userIds}", ids.joinToString(","))
                .retrieve()
                .body<List<MemberSummary>>()
                .orEmpty()
                .filter { it.userId != null && it.userId in ids }
                .associate { requireNotNull(it.userId) to MemberProfile(requireNotNull(it.userId), it.username, it.email) }
        }.onFailure { logger.warn { "member lookup failed for ${ids.size} user(s): ${it.message}" } }
            .getOrDefault(emptyMap())
    }

    /** member-service 응답 중 쓰는 필드만. 모르는 필드는 무시된다. */
    data class MemberSummary(
        val userId: String? = null,
        val username: String? = null,
        val email: String? = null,
    )
}
