package com.example.commerce.api.config

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.domain.entity.PointOutboxStatus
import com.example.commerce.application.domain.repository.ro.PointOutboxRoRepository
import com.example.commerce.application.point.PointAnomalyRecorder
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

/**
 * 포인트 아웃박스 지표.
 * - commerce_point_outbox_pending: PENDING 행 수(SPEND_GUARD 는 주문 커밋 직후 DONE 이 되므로 잠깐만 센다)
 * - commerce_point_outbox_oldest_pending_seconds: 가장 오래된 PENDING 의 나이(초). 계속 크면 point-service 장애·FAILED 직전
 * - commerce_point_outbox_failed: FAILED 행 수(재시도 소진·거절·이상 응답 — 사람이 봐야 한다. 적립은 배송 완료마다 생겨 환불보다 많다)
 * - commerce_point_reconcile_anomalies_total{type}: 대사가 찾은 이상(spend_missing, refund_missing, earn_missing, amount_mismatch)
 * 게이지는 긁을 때마다 레플리카에서 센다(status, next_attempt_at 인덱스).
 */
@Configuration
class PointOutboxMetricsConfig(
    meterRegistry: MeterRegistry,
    private val pointOutboxRoRepository: PointOutboxRoRepository,
    private val clock: Clock,
) {
    init {
        Gauge
            .builder("commerce.point.outbox.pending") { pending() }
            .description("PENDING point outbox rows")
            .register(meterRegistry)
        Gauge
            .builder("commerce.point.outbox.oldest.pending.seconds") { oldestPendingSeconds() }
            .description("Age of the oldest PENDING point outbox row in seconds (0 when none)")
            .register(meterRegistry)
        Gauge
            .builder("commerce.point.outbox.failed") { failed() }
            .description("FAILED point outbox rows (need a human)")
            .register(meterRegistry)
    }

    @Bean
    fun pointAnomalyRecorder(meterRegistry: MeterRegistry): PointAnomalyRecorder =
        PointAnomalyRecorder { type ->
            Counter
                .builder("commerce.point.reconcile.anomalies")
                .description("Point reconciliation anomalies")
                .tag("type", type)
                .register(meterRegistry)
                .increment()
        }

    private fun pending(): Double = pointOutboxRoRepository.countByStatus(PointOutboxStatus.PENDING).toDouble()

    private fun oldestPendingSeconds(): Double {
        val oldest = pointOutboxRoRepository.oldestCreatedAt(PointOutboxStatus.PENDING) ?: return 0.0
        return Duration
            .between(oldest, TierPeriods.utcNow(clock))
            .seconds
            .coerceAtLeast(0)
            .toDouble()
    }

    private fun failed(): Double = pointOutboxRoRepository.countByStatus(PointOutboxStatus.FAILED).toDouble()
}
