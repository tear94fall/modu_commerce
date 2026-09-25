package com.example.commerce.application.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/** 기획전 기간·출석 날짜는 한국 달력 날짜로 센다. 테스트는 고정 시계 빈으로 바꾼다. */
@Configuration
class ClockConfig {
    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun kstClock(): Clock = Clock.system(KST)

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
