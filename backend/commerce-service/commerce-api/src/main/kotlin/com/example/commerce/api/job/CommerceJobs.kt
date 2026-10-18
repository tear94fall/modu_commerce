package com.example.commerce.api.job

import com.example.commerce.application.domain.entity.TierRunStatus
import com.example.commerce.application.service.PointOutboxService
import com.example.commerce.application.service.PointReconcileService
import com.example.commerce.application.service.PushCampaignSendService
import com.example.commerce.application.service.TierRunService
import org.springframework.stereotype.Component

/**
 * 매월 1일 00:10(KST) 등급 산정 + 매월 쿠폰. 다른 실행이 돌고 있으면 건너뛴다(`skipped: true`).
 * [TierRunService.execute] 는 예외를 안에서 잡아 실행 행만 FAILED 로 바꾸므로, 여기서 결말을 읽어 FAILED 면 예외로 끝낸다 — 그래야 batch-service 이력에 성공으로 남지 않는다.
 */
@Component
class TierMonthlyRunJob(
    private val tierRunService: TierRunService,
) : Job {
    override val name = "tier-monthly-run"
    override val description = "회원 등급 월 산정과 월 쿠폰 발급"

    override fun run(): Map<String, Any?> {
        val runId =
            tierRunService.runMonthly() ?: return mapOf("runId" to null, "skipped" to true, "reason" to "another tier run is RUNNING")
        val result = tierRunService.result(runId)
        if (result?.status == TierRunStatus.FAILED) throw IllegalStateException("tier run $runId FAILED: ${result.message ?: "no message"}")
        return mapOf("runId" to runId, "status" to result?.status?.name)
    }
}

/** 예약 시각이 지난 푸시 캠페인을 보낸다. */
@Component
class PushCampaignsJob(
    private val pushCampaignSendService: PushCampaignSendService,
) : Job {
    override val name = "push-campaigns"
    override val description = "예약 푸시 캠페인 발송"

    override fun run(): Map<String, Any?> = mapOf("sent" to pushCampaignSendService.runDue())
}

/** PENDING 포인트 아웃박스(구매 적립·취소 환불·차감 되돌리기)를 point-service 로 보낸다. */
@Component
class PointOutboxRelayJob(
    private val pointOutboxService: PointOutboxService,
) : Job {
    override val name = "point-outbox-relay"
    override val description = "포인트 아웃박스 릴레이(적립·환불·되돌리기)"

    override fun run(): Map<String, Any?> = mapOf("relayed" to pointOutboxService.relay())
}

/** 전날(KST) 포인트 사용 주문과 배송 완료 적립 주문을 point-service 원장과 맞춰 본다. */
@Component
class PointReconcileJob(
    private val pointReconcileService: PointReconcileService,
) : Job {
    override val name = "point-reconcile"
    override val description = "전날 포인트 사용 주문 대사"

    override fun run(): Map<String, Any?> {
        val report = pointReconcileService.reconcileYesterday()
        return mapOf(
            "day" to report.day.toString(),
            "checked" to report.checked,
            "anomalies" to report.anomalies,
            "refundsQueued" to report.refundsQueued,
            "earnChecked" to report.earnChecked,
            "earnsQueued" to report.earnsQueued,
        )
    }
}
