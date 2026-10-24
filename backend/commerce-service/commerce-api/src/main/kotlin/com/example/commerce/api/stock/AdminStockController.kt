package com.example.commerce.api.stock

import com.example.commerce.application.usecase.result.StockAlertResult
import com.example.commerce.application.usecase.stock.GetStockAlertsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "재고 (어드민)",
    description = "품절·재고 부족·재고 과다 경고. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/stock")
class AdminStockController(
    private val getStockAlertsUseCase: GetStockAlertsUseCase,
) {
    @Operation(
        summary = "재고 경고",
        description =
            "판매 중 상품 가운데 품절(모든 SKU 재고 0, 찜 많은 순) 상품, 그 밖에 재고가 부족 기준 이하인 SKU(적은 순), " +
                "재고가 과다 기준 이상인 SKU(많은 순)를 돌려주고 줄마다 최근 30일 판매 수량을 붙인다. " +
                "기준을 주지 않으면 서버 설정값(부족 5, 과다 100). 부족 0~1000, 과다 1~100000, 과다 > 부족이 아니면 400.",
    )
    @GetMapping("/alerts")
    fun alerts(
        @Parameter(description = "재고 부족으로 볼 기준(이 수량 이하). 0~1000", example = "5")
        @RequestParam(required = false) low: Int?,
        @Parameter(description = "재고 과다로 볼 기준(이 수량 이상). 1~100000", example = "100")
        @RequestParam(required = false) high: Int?,
    ): StockAlertResult = getStockAlertsUseCase.execute(low, high)
}
