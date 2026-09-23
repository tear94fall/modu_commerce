package com.example.commerce.application.point

import org.springframework.stereotype.Component

/** 이 모듈의 스프링 테스트용. 실제 구현(point-service 호출)은 commerce-api 에 있다. */
@Component
class StubPointGateway : PointGateway {
    override fun spend(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ) = Unit

    override fun refund(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ) = Unit
}
