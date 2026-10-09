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

    override fun earn(
        userId: String,
        ruleCode: String,
        refId: String,
        memo: String?,
    ) = PointEarnResult(applied = true, amount = 10)

    override fun earnAmount(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ) = PointEarnResult(applied = true, amount = amount)

    override fun refund(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ) = Unit

    override fun cancelSpend(
        userId: String,
        spendRefId: String,
        memo: String?,
    ) = PointCancelResult(cancelled = false, reason = PointCancelResult.NO_SPEND, amount = 0, balance = 0)

    override fun findTransactions(refs: List<PointRef>): List<PointRefTransaction> = emptyList()
}
