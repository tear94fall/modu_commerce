package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.domain.repository.rw.CustomerRwRepository
import com.example.commerce.application.domain.repository.rw.TierRwRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * 구매 적립. 주문이 배송 완료되는 트랜잭션 안에서 고객의 지금 등급 적립률로 금액을 정해 주문에 적고([Order.decideEarn]),
 * 적립액이 있으면 같은 트랜잭션에 EARN 아웃박스 행을 남긴다([PointOutboxService.earnOnDelivered]). 보내기는 커밋 뒤.
 * 트랜잭션은 호출자(OrderCommandService)의 것을 그대로 쓴다 — 롤백되면 금액도 행도 남지 않는다.
 */
@Service
class PurchaseEarnService(
    private val customerRwRepository: CustomerRwRepository,
    private val tierRwRepository: TierRwRepository,
    private val pointOutboxService: PointOutboxService,
) {
    /** 적립액 = 결제 금액(상품 − 쿠폰 − 포인트) × 등급 적립률 ÷ 100 내림. 고객이 아니거나(탈퇴 포함) 0P 면 0 으로 적고 끝. */
    fun onDelivered(order: Order) {
        val tier = tierOf(order.userId)
        if (tier == null) {
            order.decideEarn(0, 0)
            return
        }
        order.decideEarn(tier.earnFor(order.paymentAmount()), tier.earnRate)
        if ((order.earnPoints ?: 0) > 0) pointOutboxService.earnOnDelivered(order)
    }

    private fun tierOf(userId: String): Tier? =
        customerRwRepository
            .findByIdOrNull(userId)
            ?.takeIf { it.isActive() }
            ?.let { tierRwRepository.findByIdOrNull(it.tierCode) }
}
