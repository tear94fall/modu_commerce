package com.example.commerce.api.point

import com.example.commerce.application.common.logger
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.point.PointGatewayException
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

    private fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (e: PointUnavailableException) {
            throw PointGatewayException(e)
        }
}
