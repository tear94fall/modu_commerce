package com.example.commerce.api.config

import com.example.commerce.api.job.JobRunner
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * 회원 등급 스케줄러. [schedulerEnabled]: 매월 1일 00:10(KST) 등급 산정 + 매월 쿠폰.
 * 테스트는 끄고 서비스를 직접 부른다. 회차는 [JobRunner] 로 돌린다(수동 실행 `/api-internal/v1/jobs` 와 같은 길).
 * 구매 적립 재시도는 없다 — 적립은 포인트 아웃박스 릴레이(PointOutboxRelayConfig)가 보낸다.
 */
@ConfigurationProperties("modu.tier")
data class ModuTierProperties(
    val schedulerEnabled: Boolean = true,
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
        jobRunner.runIfIdle("tier-monthly-run")
    }
}
