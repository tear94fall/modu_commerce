package com.example.commerce.api.config

import com.example.commerce.api.job.JobRunner
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * 회원 등급 스케줄러.
 * [schedulerEnabled]: 매월 1일 00:10(KST) 등급 산정 + 매월 쿠폰. [earnRetryEnabled]: 10분마다 PENDING 구매 적립 재시도.
 * 테스트는 둘 다 끄고 서비스를 직접 부른다. 회차는 [JobRunner] 로 돌린다(수동 실행 `/api-internal/v1/jobs` 와 같은 길).
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
    private val jobRunner: JobRunner,
) {
    // 파드가 여러 개여도 한 곳에서만 돈다(SchedulerLockConfig). 월 산정은 길 수 있어 최대 30분까지 잠근다.
    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "commerce:tier-monthly-run", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    fun monthlyTierRun() {
        jobRunner.run("tier-monthly-run")
    }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "modu.tier", name = ["earn-retry-enabled"], havingValue = "true", matchIfMissing = true)
class PurchaseEarnRetryConfig(
    private val jobRunner: JobRunner,
) {
    // 파드가 여러 개여도 한 곳에서만 돈다(SchedulerLockConfig). 재시도는 짧으니 최대 5분.
    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    @SchedulerLock(name = "commerce:purchase-earn-retry", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    fun retryPendingEarns() {
        jobRunner.run("purchase-earn-retry")
    }
}
