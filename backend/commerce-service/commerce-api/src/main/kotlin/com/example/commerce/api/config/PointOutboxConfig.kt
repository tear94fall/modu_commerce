package com.example.commerce.api.config

import com.example.commerce.api.logging.JobMdc
import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.PointOutboxStatus
import com.example.commerce.application.domain.repository.ro.PointOutboxRoRepository
import com.example.commerce.application.point.PointAnomalyRecorder
import com.example.commerce.application.service.PointOutboxService
import com.example.commerce.application.service.PointReconcileService
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration

/**
 * 포인트 아웃박스 지표.
 * - commerce_point_outbox_pending: PENDING 행 수(SPEND_GUARD 는 주문 커밋 직후 DONE 이 되므로 잠깐만 센다)
 * - commerce_point_outbox_oldest_pending_seconds: 가장 오래된 PENDING 의 나이(초). 계속 크면 point-service 장애·FAILED 직전
 * - commerce_point_reconcile_anomalies_total{type}: 대사가 찾은 이상(spend_missing, refund_missing, amount_mismatch)
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
}

/** 1분마다 PENDING 아웃박스를 보낸다(차감 되돌리기·취소 환불). 파드가 여러 개여도 한 곳에서만 돈다(SchedulerLockConfig). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "modu.point.outbox", name = ["relay-enabled"], havingValue = "true", matchIfMissing = true)
class PointOutboxRelayConfig(
    private val pointOutboxService: PointOutboxService,
) {
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @SchedulerLock(name = "commerce:point-outbox-relay", lockAtLeastFor = "PT10S", lockAtMostFor = "PT5M")
    fun relay() {
        JobMdc.run("point-outbox-relay") {
            try {
                val done = pointOutboxService.relay()
                if (done > 0) logger.info { "point outbox relay: $done row(s) done" }
            } catch (e: Exception) {
                logger.error(e) { "point outbox relay failed" }
            }
        }
    }
}

/** 매일 04:00(KST) 전날 포인트 사용 주문을 point-service 원장과 맞춰 본다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "modu.point.outbox", name = ["reconcile-enabled"], havingValue = "true", matchIfMissing = true)
class PointReconcileConfig(
    private val pointReconcileService: PointReconcileService,
) {
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "commerce:point-reconcile", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    fun reconcile() {
        JobMdc.run("point-reconcile") {
            try {
                pointReconcileService.reconcileYesterday()
            } catch (e: Exception) {
                logger.error(e) { "point reconcile failed" }
            }
        }
    }
}
