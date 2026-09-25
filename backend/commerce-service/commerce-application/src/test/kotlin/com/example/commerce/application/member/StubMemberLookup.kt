package com.example.commerce.application.member

import org.springframework.stereotype.Component

/** 이 모듈의 스프링 테스트용. 실제 구현(member-service 호출)은 commerce-api 에 있다. */
@Component
class StubMemberLookup : MemberLookup {
    override fun find(userId: String): MemberProfile? = null
}
