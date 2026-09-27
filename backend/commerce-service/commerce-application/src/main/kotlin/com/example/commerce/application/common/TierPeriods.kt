package com.example.commerce.application.common

import java.time.Clock
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 회원 등급 기간 계산. 기간은 한국 달력의 달로 세고, DB 시각(UTC)과 비교할 수 있게 UTC LocalDateTime 으로 돌려준다.
 *
 * - 기준 기간(basis): 이번 달 1일 기준 지난 6개월 = [6개월 전 1일, 이번 달 1일). 10월 1일 산정이면 4월~9월.
 * - 누적 기간(rolling): 다음 산정에 쓰일 기간 = [5개월 전 1일, 다음 달 1일). 지금까지의 금액이다.
 */
object TierPeriods {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val LABEL = DateTimeFormatter.ofPattern("yyyy.MM")
    private val KEY = DateTimeFormatter.ofPattern("yyyy-MM")

    fun utcNow(clock: Clock): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    fun currentMonth(clock: Clock): YearMonth = YearMonth.from(clock.instant().atZone(KST))

    /** 그 달 1일 0시(KST)를 UTC 로. */
    fun startUtc(month: YearMonth): LocalDateTime = LocalDateTime.ofInstant(month.atDay(1).atStartOfDay(KST).toInstant(), ZoneOffset.UTC)

    fun basisFrom(month: YearMonth): LocalDateTime = startUtc(month.minusMonths(6))

    fun basisTo(month: YearMonth): LocalDateTime = startUtc(month)

    fun rollingFrom(month: YearMonth): LocalDateTime = startUtc(month.minusMonths(5))

    fun rollingTo(month: YearMonth): LocalDateTime = startUtc(month.plusMonths(1))

    /** 기준 기간 이름. 2026-10 → "2026.04 ~ 2026.09". */
    fun basisLabel(month: YearMonth): String = "${month.minusMonths(6).format(LABEL)} ~ ${month.minusMonths(1).format(LABEL)}"

    /** 등급 쿠폰 발급 키에 쓰는 달. "2026-10". */
    fun key(month: YearMonth): String = month.format(KEY)
}
