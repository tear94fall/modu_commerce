package com.example.commerce.api.stats

import com.example.commerce.api.common.AdminZone
import com.example.commerce.application.usecase.result.AdminStatsResult
import com.example.commerce.application.usecase.stats.GetAdminStatsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "통계 (어드민)",
    description = "어드민 콘솔 홈 \"오늘의 숫자\". 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/stats")
class AdminStatsController(
    private val getAdminStatsUseCase: GetAdminStatsUseCase,
) {
    @Operation(
        summary = "주문 통계(백오피스 홈)",
        description = "표시 시간대(zone)의 달력 날짜로 묶은 최근 days 일(1~92, 오늘 포함)의 결제 주문 수·실결제액·쿠폰 사용 주문 수·취소 수.",
    )
    @GetMapping
    fun stats(
        @Parameter(description = "며칠 치(오늘 포함). 1~92", example = "7") @RequestParam(defaultValue = "7") days: Int,
        @Parameter(description = "날짜를 묶을 시간대(IANA)", example = "Asia/Seoul") @RequestParam(defaultValue = "Asia/Seoul") zone: String,
    ): AdminStatsResult = getAdminStatsUseCase.execute(days, AdminZone.parse(zone))
}
