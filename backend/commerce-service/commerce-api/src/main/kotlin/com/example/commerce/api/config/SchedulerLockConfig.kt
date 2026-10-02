package com.example.commerce.api.config

import com.zaxxer.hikari.HikariDataSource
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 스케줄러 분산 잠금(ShedLock).
 * 파드가 2개 이상 떠도 `@Scheduled` 작업(등급 산정·구매 적립 재시도·푸시 캠페인)이 한 곳에서만 돌게 `shedlock` 테이블의 행을 잡는다.
 * 테이블은 DBA 가 만든다(modu_infra/data/mysql/schema). 테스트(H2)는 src/test/resources/schema-shedlock.sql 로 만든다.
 *
 * 잠금은 반드시 rw(master) 풀로 간다. 레플리카는 읽기 전용이라 INSERT/UPDATE 가 실패하고, 복제 지연 때문에
 * 두 파드가 서로의 잠금을 못 볼 수도 있다. 그래서 지연 프록시(rwDataSource)가 아닌 실제 master Hikari 풀을 직접 쓴다.
 * `usingDbTime()`: 파드마다 시계가 달라도 lock_until 을 DB 시각으로 계산한다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulerLockConfig {
    @Bean
    fun lockProvider(
        @Qualifier("rwHikariDataSource") master: HikariDataSource,
    ): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(JdbcTemplate(master))
                .usingDbTime()
                .build(),
        )
}
