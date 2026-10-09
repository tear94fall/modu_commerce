package com.example.commerce.application.service

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.PointOutboxStatus
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.point.PointAnomalyRecorder
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.point.PointRef
import com.example.commerce.application.point.PointRefTransaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/** 대사에 쓰는 주문 값(트랜잭션 밖에서 쓴다). 시각은 UTC. */
data class OrderPointSnapshot(
    val orderNo: String,
    val userId: String,
    val status: OrderStatus,
    val pointAmount: Long,
    val cancelledAt: LocalDateTime?,
) {
    val spendRefId: String get() = "order:$orderNo"
    val refundRefId: String get() = "refund:order:$orderNo"
}

@Service
class PointReconcileQueryService(
    private val orderRoRepository: OrderRoRepository,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun pointOrders(
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<OrderPointSnapshot> =
        orderRoRepository.findPointOrdersBetween(from, to).map {
            OrderPointSnapshot(it.orderNo, it.userId, it.status, it.pointAmount, it.cancelledAt)
        }
}

/** 대사 결과. [anomalies] 는 종류 → 건수. */
data class PointReconcileReport(
    val day: LocalDate,
    val checked: Int,
    val anomalies: Map<String, Int>,
    val refundsQueued: Int,
)

/**
 * 포인트 대사(매일 04:00 KST, 전날 하루). 전날(KST) 만들었거나 취소한 포인트 사용 주문을 point-service 원장과 맞춰 본다.
 * - 결제완료·배송중·배송완료(그리고 취소) 주문에 차감(order:<번호>)이 없음 → ERROR "point spend missing"
 * - 취소한 지 1시간이 넘었는데 환불(refund:order:<번호>)이 없음 → REFUND 아웃박스가 없으면 만들어 릴레이가 보내게 한다
 * - 금액이 다름 → ERROR
 * 찾은 이상은 commerce_point_reconcile_anomalies_total{type} 로 센다.
 */
@Service
class PointReconcileService(
    private val queryService: PointReconcileQueryService,
    private val outboxCommandService: PointOutboxCommandService,
    private val pointGateway: PointGateway,
    private val anomalyRecorder: PointAnomalyRecorder,
    private val clock: Clock,
) {
    /** 어제(KST) 하루. */
    fun reconcileYesterday(): PointReconcileReport = reconcile(LocalDate.now(clock.withZone(TierPeriods.KST)).minusDays(1))

    fun reconcile(day: LocalDate): PointReconcileReport {
        val from = utc(day)
        val to = utc(day.plusDays(1))
        val now = TierPeriods.utcNow(clock)
        val orders = queryService.pointOrders(from, to)
        val refs =
            orders.flatMap { o ->
                listOfNotNull(
                    PointRef(o.userId, o.spendRefId),
                    if (o.status ==
                        OrderStatus.CANCELLED
                    ) {
                        PointRef(o.userId, o.refundRefId)
                    } else {
                        null
                    },
                )
            }
        val ledger =
            refs
                .chunked(PointGateway.MAX_REFS)
                .flatMap { pointGateway.findTransactions(it) }
                .associateBy { it.userId to it.refId }
        val anomalies = mutableMapOf<String, Int>()
        var queued = 0

        fun anomaly(type: String) {
            anomalies.merge(type, 1, Int::plus)
            anomalyRecorder.record(type)
        }

        orders.forEach { o ->
            val spend = ledger[o.userId to o.spendRefId]
            if (spend == null) {
                logger.error { "point spend missing: order ${o.orderNo} (${o.status}) user=${o.userId} ${o.pointAmount}P" }
                anomaly(SPEND_MISSING)
            } else if (mismatch(spend, o.pointAmount)) {
                logger.error { "point amount mismatch: ${o.spendRefId} ledger=${spend.amount} order=${o.pointAmount}" }
                anomaly(AMOUNT_MISMATCH)
            }
            if (o.status != OrderStatus.CANCELLED || spend == null) return@forEach
            val refund = ledger[o.userId to o.refundRefId]
            if (refund != null) {
                if (mismatch(refund, o.pointAmount)) {
                    logger.error { "point amount mismatch: ${o.refundRefId} ledger=${refund.amount} order=${o.pointAmount}" }
                    anomaly(AMOUNT_MISMATCH)
                }
                return@forEach
            }
            val cancelledAt = o.cancelledAt ?: return@forEach
            if (Duration.between(cancelledAt, now) < REFUND_GRACE) return@forEach
            anomaly(REFUND_MISSING)
            when (val existing = outboxCommandService.ensureRefund(o.userId, o.orderNo, o.spendRefId, o.pointAmount)) {
                null -> {
                    queued++
                    logger.warn { "point refund missing for cancelled order ${o.orderNo}: REFUND outbox queued" }
                }
                PointOutboxStatus.PENDING -> logger.warn { "point refund missing for cancelled order ${o.orderNo}: outbox still retrying" }
                else -> logger.error { "point refund missing for cancelled order ${o.orderNo} but outbox is $existing" }
            }
        }
        logger.info { "point reconcile $day: ${orders.size} order(s), anomalies=$anomalies, refunds queued=$queued" }
        return PointReconcileReport(day, orders.size, anomalies, queued)
    }

    private fun mismatch(
        tx: PointRefTransaction,
        expected: Long,
    ): Boolean = abs(tx.amount) != expected

    private fun utc(day: LocalDate): LocalDateTime = LocalDateTime.ofInstant(day.atStartOfDay(TierPeriods.KST).toInstant(), ZoneOffset.UTC)

    companion object {
        const val SPEND_MISSING = "spend_missing"
        const val REFUND_MISSING = "refund_missing"
        const val AMOUNT_MISMATCH = "amount_mismatch"
        val REFUND_GRACE: Duration = Duration.ofHours(1)
    }
}
