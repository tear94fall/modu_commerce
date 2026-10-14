package com.example.commerce.application.service

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.EarnStatus
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.domain.repository.rw.CustomerRwRepository
import com.example.commerce.application.domain.repository.rw.OrderRwRepository
import com.example.commerce.application.domain.repository.rw.TierRwRepository
import com.example.commerce.application.point.PointEarnRejectedException
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.push.PushAsyncRunner
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock

/** point-service 에 보낼 적립 한 건. */
data class PurchaseEarnRequest(
    val orderId: Long,
    val userId: String,
    val points: Long,
    val refId: String,
    val memo: String,
)

/**
 * 구매 적립의 DB 단계. 커밋 뒤(afterCommit)에 불리므로 늘 새 트랜잭션을 연다
 * (afterCommit 안에서 REQUIRED 로 부르면 이미 커밋된 트랜잭션에 붙어 쓰기가 사라진다).
 */
@Service
@Transactional(transactionManager = "rwTransactionManager", propagation = Propagation.REQUIRES_NEW)
class PurchaseEarnCommandService(
    private val orderRwRepository: OrderRwRepository,
    private val customerRwRepository: CustomerRwRepository,
    private val tierRwRepository: TierRwRepository,
    private val clock: Clock,
) {
    /**
     * 배송 완료 주문의 적립액을 정한다(고객의 지금 등급 적립률, 결제 금액 × 적립률 ÷ 100 내림).
     * 고객이 아니거나 0P 면 NONE 으로 끝. 보낼 게 있으면(PENDING) 요청을 돌려준다.
     */
    fun decide(orderId: Long): PurchaseEarnRequest? {
        val order = orderRwRepository.findByIdForUpdate(orderId) ?: return null
        if (order.status != OrderStatus.DELIVERED) return null
        if (order.earnPoints != null) return if (order.earnStatus == EarnStatus.PENDING) requestOf(order) else null
        val customer = customerRwRepository.findByIdOrNull(order.userId)?.takeIf { it.isActive() }
        val tier = customer?.let { tierOf(it.tierCode) }
        if (tier == null) {
            order.decideEarn(0, 0)
            return null
        }
        order.decideEarn(tier.earnFor(order.paymentAmount()), tier.earnRate)
        return if (order.earnStatus == EarnStatus.PENDING) requestOf(order, tier) else null
    }

    /** 재시도할 PENDING 주문의 요청. */
    fun pending(orderId: Long): PurchaseEarnRequest? {
        val order = orderRwRepository.findByIdOrNull(orderId)?.takeIf { it.earnStatus == EarnStatus.PENDING } ?: return null
        return requestOf(order)
    }

    fun pendingIds(): List<Long> = orderRwRepository.findPendingEarnIds()

    fun done(orderId: Long) {
        orderRwRepository.findByIdOrNull(orderId)?.takeIf { it.earnStatus == EarnStatus.PENDING }?.earned(TierPeriods.utcNow(clock))
    }

    fun failed(orderId: Long) {
        orderRwRepository.findByIdOrNull(orderId)?.takeIf { it.earnStatus == EarnStatus.PENDING }?.earnFailed()
    }

    private fun tierOf(code: String): Tier? = tierRwRepository.findByIdOrNull(code)

    private fun requestOf(
        order: Order,
        tier: Tier? = customerRwRepository.findByIdOrNull(order.userId)?.let { tierOf(it.tierCode) },
    ): PurchaseEarnRequest {
        val rate = order.earnRate ?: 0
        val label = tier?.name?.let { "$it $rate%" } ?: "$rate%"
        return PurchaseEarnRequest(
            orderId = requireNotNull(order.id),
            userId = order.userId,
            points = requireNotNull(order.earnPoints),
            refId = order.purchaseEarnRefId(),
            memo = "구매 적립 · 주문 ${order.orderNo} ($label)",
        )
    }
}

/**
 * 구매 적립. 주문이 배송 완료되면 커밋 뒤에 point-service `earn-amount` 를 부른다.
 * 포인트 서버 장애면 PENDING 으로 남고 [retryPending](10분마다)이 같은 멱등 키로 다시 보낸다.
 */
@Service
class PurchaseEarnService(
    private val purchaseEarnCommandService: PurchaseEarnCommandService,
    private val pointGateway: PointGateway,
    private val asyncRunner: PushAsyncRunner,
) {
    /** 지금 트랜잭션이 커밋되면 [orderId] 의 적립을 한다(트랜잭션 밖이면 바로). */
    fun afterCommit(orderId: Long) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            asyncRunner.run { earn(orderId) }
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    asyncRunner.run { earn(orderId) }
                }
            },
        )
    }

    fun earn(orderId: Long) {
        val request = purchaseEarnCommandService.decide(orderId) ?: return
        send(request)
    }

    /** PENDING 적립을 다시 보낸다. 적립된 수를 돌려준다. */
    fun retryPending(): Int =
        purchaseEarnCommandService.pendingIds().count { id ->
            val request = purchaseEarnCommandService.pending(id) ?: return@count false
            send(request)
        }

    private fun send(request: PurchaseEarnRequest): Boolean =
        try {
            val result = pointGateway.earnAmount(request.userId, request.points, request.refId, request.memo)
            if (result.applied || result.reason == "DUPLICATE") {
                purchaseEarnCommandService.done(request.orderId)
                true
            } else {
                logger.warn { "purchase earn ${request.refId} not applied: ${result.reason}" }
                purchaseEarnCommandService.failed(request.orderId)
                false
            }
        } catch (e: PointEarnRejectedException) {
            logger.warn { "purchase earn ${request.refId} rejected: ${e.message}" }
            purchaseEarnCommandService.failed(request.orderId)
            false
        } catch (e: Exception) {
            logger.warn { "purchase earn ${request.refId} pending (point-service unavailable): ${e.message}" }
            false
        }
}
