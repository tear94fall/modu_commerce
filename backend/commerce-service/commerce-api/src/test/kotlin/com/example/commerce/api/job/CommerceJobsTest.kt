package com.example.commerce.api.job

import com.example.commerce.application.service.PointOutboxService
import com.example.commerce.application.service.PointReconcileReport
import com.example.commerce.application.service.PointReconcileService
import com.example.commerce.application.service.PurchaseEarnService
import com.example.commerce.application.service.PushCampaignSendService
import com.example.commerce.application.service.TierRunService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDate

/** 잡 5개는 기존 서비스 메서드를 감싸고 결과 요약(건수)을 Map 으로 돌려준다. 이름은 JobMdc 에 쓰던 그대로. */
class CommerceJobsTest {
    @Test
    fun `등급 월 산정 잡은 run id 를 결과로, 건너뛴 회차는 null 을 돌려준다`() {
        val service = mock(TierRunService::class.java)
        `when`(service.runMonthly()).thenReturn(7L, null)
        val job = TierMonthlyRunJob(service)

        assertEquals("tier-monthly-run", job.name)
        assertEquals(mapOf("runId" to 7L), job.run())
        assertEquals(mapOf("runId" to null), job.run())
    }

    @Test
    fun `건수를 돌려주는 잡 셋 — 적립 재시도·푸시 캠페인·아웃박스 릴레이`() {
        val earn = mock(PurchaseEarnService::class.java)
        `when`(earn.retryPending()).thenReturn(3)
        val push = mock(PushCampaignSendService::class.java)
        `when`(push.runDue()).thenReturn(2)
        val outbox = mock(PointOutboxService::class.java)
        `when`(outbox.relay()).thenReturn(5)

        assertEquals("purchase-earn-retry", PurchaseEarnRetryJob(earn).name)
        assertEquals(mapOf("credited" to 3), PurchaseEarnRetryJob(earn).run())
        assertEquals("push-campaigns", PushCampaignsJob(push).name)
        assertEquals(mapOf("sent" to 2), PushCampaignsJob(push).run())
        assertEquals("point-outbox-relay", PointOutboxRelayJob(outbox).name)
        assertEquals(mapOf("relayed" to 5), PointOutboxRelayJob(outbox).run())
    }

    @Test
    fun `포인트 대사 잡은 보고서를 평평한 결과로 돌려준다`() {
        val service = mock(PointReconcileService::class.java)
        `when`(service.reconcileYesterday())
            .thenReturn(PointReconcileReport(LocalDate.of(2026, 10, 16), 12, mapOf("point spend missing" to 1), 2))
        val job = PointReconcileJob(service)

        assertEquals("point-reconcile", job.name)
        assertEquals(
            mapOf("day" to "2026-10-16", "checked" to 12, "anomalies" to mapOf("point spend missing" to 1), "refundsQueued" to 2),
            job.run(),
        )
    }
}
