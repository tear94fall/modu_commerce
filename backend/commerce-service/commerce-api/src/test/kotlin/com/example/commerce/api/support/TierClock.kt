package com.example.commerce.api.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 등급 테스트용 시계. 한국 시각으로 옮긴다. */
class TierClock : Clock() {
    var now: Instant = kst(2026, 9, 28, 12, 0)

    fun setKst(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
    ) {
        now = kst(year, month, day, hour, minute)
    }

    override fun getZone(): ZoneId = KST

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")

        fun kst(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
        ): Instant = LocalDateTime.of(year, month, day, hour, minute, second).atZone(KST).toInstant()

        /** 한국 시각 → DB 에 넣는 UTC LocalDateTime. */
        fun utc(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
        ): LocalDateTime = LocalDateTime.ofInstant(kst(year, month, day, hour, minute, second), java.time.ZoneOffset.UTC)
    }

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun tierClock(): TierClock = TierClock()
    }
}
