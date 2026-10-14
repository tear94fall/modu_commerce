package com.example.commerce.application.member

/** member-service 가 아는 회원 정보 중 커머스가 복사해 두는 부분. */
data class MemberProfile(
    val userId: String,
    val username: String?,
    val email: String?,
)

/** 회원 이름·이메일 조회. 구현(commerce-api)은 모두 챗 member-service 내부 API 를 부르며, 실패하면 null 을 준다. */
fun interface MemberLookup {
    fun find(userId: String): MemberProfile?

    /** 여러 명(백오피스 고객 목록 한 페이지). 구현은 한 번에 부른다. 못 찾은 사람은 빠진다. */
    fun findAll(userIds: Collection<String>): Map<String, MemberProfile> =
        userIds.distinct().mapNotNull { id -> find(id)?.let { id to it } }.toMap()
}
