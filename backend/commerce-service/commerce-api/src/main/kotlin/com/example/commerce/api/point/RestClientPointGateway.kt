package com.example.commerce.api.point

import com.example.commerce.application.common.logger
import com.example.commerce.application.point.PointCancelResult
import com.example.commerce.application.point.PointEarnResult
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.point.PointGatewayException
import com.example.commerce.application.point.PointRef
import com.example.commerce.application.point.PointRefTransaction
import org.springframework.stereotype.Component

/** 주문 서비스가 쓰는 포인트 포트 구현. 연결 실패는 [PointGatewayException](503)으로 바꿔 주문을 만들지 않는다. */
@Component
class RestClientPointGateway(
    private val pointClient: PointClient,
) : PointGateway {
    override fun spend(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ) {
        val result = wrap { pointClient.spend(userId, amount, refId, memo) }
        logger.info { "point spend $refId: applied=${result.applied} balance=${result.balance}" }
    }

    override fun refund(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ) {
        val result = wrap { pointClient.refund(userId, amount, refId, memo) }
        logger.info { "point refund $refId: applied=${result.applied} balance=${result.balance}" }
    }

    override fun cancelSpend(
        userId: String,
        spendRefId: String,
        memo: String?,
    ): PointCancelResult {
        val result = wrap { pointClient.cancelSpend(userId, spendRefId, memo) }
        logger.info { "point spend cancel $spendRefId: cancelled=${result.cancelled} reason=${result.reason} amount=${result.amount}" }
        return PointCancelResult(result.cancelled, result.reason, result.amount, result.balance)
    }

    override fun findTransactions(refs: List<PointRef>): List<PointRefTransaction> {
        require(refs.size <= PointGateway.MAX_REFS) { "refs 는 한 번에 ${PointGateway.MAX_REFS}개까지입니다." }
        if (refs.isEmpty()) return emptyList()
        val result = wrap { pointClient.refs(refs.map { PointRefKey(it.userId, it.refId) }) }
        return result.transactions.map { PointRefTransaction(it.userId, it.refId, it.type, it.amount, it.createdDate) }
    }

    override fun earn(
        userId: String,
        ruleCode: String,
        refId: String,
        memo: String?,
    ): PointEarnResult {
        val result = wrap { pointClient.earn(userId, ruleCode, refId, memo) }
        logger.info { "point earn $refId ($ruleCode): applied=${result.applied} amount=${result.amount} reason=${result.reason}" }
        return PointEarnResult(result.applied, result.amount, result.reason)
    }

    override fun earnAmount(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ): PointEarnResult {
        val result = wrap { pointClient.earnAmount(userId, amount, PURCHASE, refId, memo) }
        logger.info { "point earn-amount $refId ($amount): applied=${result.applied} amount=${result.amount} reason=${result.reason}" }
        return PointEarnResult(result.applied, result.amount, result.reason)
    }

    private fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (e: PointUnavailableException) {
            throw PointGatewayException(e)
        }

    companion object {
        /** 구매 적립의 point-service reason. */
        const val PURCHASE = "PURCHASE"
    }
}
