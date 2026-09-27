package com.example.commerce.api.config

import com.example.commerce.application.common.logger
import com.example.commerce.application.service.PurchaseEarnService
import com.example.commerce.application.service.TierRunService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * 회원 등급 스케줄러.
 * [schedulerEnabled]: 매월 1일 00:10(KST) 등급 산정 + 매월 쿠폰. [earnRetryEnabled]: 10분마다 PENDING 구매 적립 재시도.
 * 테스트는 둘 다 끄고 서비스를 직접 부른다.
 */
@ConfigurationProperties("modu.tier")
data class ModuTierProperties(
    val schedulerEnabled: Boolean = true,
    val earnRetryEnabled: Boolean = true,
)

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "modu.tier", name = ["scheduler-enabled"], havingValue = "true", matchIfMissing = true)
class TierSchedulingConfig(
    private val tierRunService: TierRunService,
) {
    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Seoul")
    fun monthlyTierRun() {
        try {
            tierRunService.runMonthly()
        } catch (e: Exception) {
            logger.error(e) { "monthly tier run failed" }
        }
    }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "modu.tier", name = ["earn-retry-enabled"], havingValue = "true", matchIfMissing = true)
class PurchaseEarnRetryConfig(
    private val purchaseEarnService: PurchaseEarnService,
) {
    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    fun retryPendingEarns() {
        try {
            val done = purchaseEarnService.retryPending()
            if (done > 0) logger.info { "purchase earn retry: $done order(s) credited" }
        } catch (e: Exception) {
            logger.error(e) { "purchase earn retry failed" }
        }
    }
}
