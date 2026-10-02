package com.example.commerce.api.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 준비 검사(/actuator/health/readiness)가 보는 indicator.
 * 자동 `db` indicator 는 rw·ro 데이터소스를 모두 검사해 레플리카가 죽으면 서비스 전체가 unready 가 된다.
 * 그래서 rw(master) 풀만 보는 `masterDb` 를 따로 두고 readiness 그룹에는 이것만 넣는다(application.yml management.endpoint.health.group).
 * 지연 프록시(rwDataSource)가 아니라 실제 Hikari 풀을 보므로 검사 때 바로 연결을 확인한다.
 */
@Configuration
class HealthConfig {
    @Bean
    fun masterDbHealthIndicator(
        @Qualifier("rwHikariDataSource") master: HikariDataSource,
    ): HealthIndicator = DataSourceHealthIndicator(master)
}
