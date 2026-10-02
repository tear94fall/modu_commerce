package com.example.commerce.api.config

import com.example.commerce.application.service.PurchaseEarnService
import com.example.commerce.application.service.PushCampaignSendService
import com.example.commerce.application.service.TierRunService
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.time.Instant

/**
 * 스케줄러 분산 잠금(SchedulerLockConfig). 다른 파드가 잠금을 쥐고 있으면 작업 본문이 돌지 않고, 잠금이 없으면 돈다.
 * 스케줄러를 켜야 `@SchedulerLock` 프록시가 만들어지므로 켜되, 작업 본문(서비스)은 목으로 두어 실제로는 아무것도 하지 않는다.
 * 잠금 이름마다 테스트 하나만 쓴다(작업이 한 번 돌면 lockAtLeastFor=30초 동안 잠겨 있어 다른 테스트와 섞이면 안 된다).
 */
@SpringBootTest(
    properties = [
        "modu.tier.scheduler-enabled=true",
        "modu.tier.earn-retry-enabled=true",
        "modu.push.scheduler-enabled=true",
    ],
)
class SchedulerLockTest
    @Autowired
    constructor(
        private val lockProvider: LockProvider,
        private val tierScheduling: TierSchedulingConfig,
        private val earnRetry: PurchaseEarnRetryConfig,
        private val pushScheduling: PushSchedulingConfig,
    ) {
        @MockitoBean
        private lateinit var tierRunService: TierRunService

        @MockitoBean
        private lateinit var purchaseEarnService: PurchaseEarnService

        @MockitoBean
        private lateinit var pushCampaignSendService: PushCampaignSendService

        @BeforeEach
        fun reset() {
            clearInvocations(tierRunService, purchaseEarnService, pushCampaignSendService)
        }

        /** 다른 파드가 잡은 것처럼 잠금을 먼저 쥔다. lockAtLeastFor=0 이라 unlock 하면 바로 풀린다. */
        private fun holdLock(name: String): SimpleLock {
            val lock =
                lockProvider.lock(
                    LockConfiguration(Instant.now(), name, Duration.ofMinutes(5), Duration.ZERO),
                )
            assertTrue(lock.isPresent, "lock $name should be free before the test")
            return lock.get()
        }

        @Test
        fun `월 등급 산정은 잠금이 잡혀 있으면 돌지 않고 풀리면 돈다`() {
            val held = holdLock("commerce:tier-monthly-run")
            tierScheduling.monthlyTierRun()
            verify(tierRunService, never()).runMonthly()

            held.unlock()
            tierScheduling.monthlyTierRun()
            verify(tierRunService).runMonthly()
        }

        @Test
        fun `구매 적립 재시도는 잠금이 잡혀 있으면 돌지 않고 풀리면 돈다`() {
            val held = holdLock("commerce:purchase-earn-retry")
            earnRetry.retryPendingEarns()
            verify(purchaseEarnService, never()).retryPending()

            held.unlock()
            earnRetry.retryPendingEarns()
            verify(purchaseEarnService).retryPending()
        }

        @Test
        fun `푸시 캠페인 발송은 잠금이 잡혀 있으면 돌지 않고 풀리면 돈다`() {
            val held = holdLock("commerce:push-campaigns")
            pushScheduling.sendDueCampaigns()
            verify(pushCampaignSendService, never()).runDue()

            held.unlock()
            pushScheduling.sendDueCampaigns()
            verify(pushCampaignSendService).runDue()
        }

        @Test
        fun `lockAtLeastFor 동안은 풀어도 다시 잡히지 않는다`() {
            // 작업들이 lockAtLeastFor=30초를 두는 이유: 빨리 끝난 작업 뒤에 다른 파드가 바로 같은 작업을 또 돌리지 않게.
            // 작업 이름은 위 테스트들이 쓰므로 LockProvider 로 직접 확인한다(H2 의 shedlock 테이블, DB 시각 기준).
            val name = "commerce:test-at-least-for"
            val first = lockProvider.lock(LockConfiguration(Instant.now(), name, Duration.ofMinutes(5), Duration.ofSeconds(30)))
            assertTrue(first.isPresent)
            first.get().unlock()
            val second = lockProvider.lock(LockConfiguration(Instant.now(), name, Duration.ofMinutes(5), Duration.ZERO))
            assertTrue(second.isEmpty, "lock should still be held for lockAtLeastFor after unlock")
        }
    }
