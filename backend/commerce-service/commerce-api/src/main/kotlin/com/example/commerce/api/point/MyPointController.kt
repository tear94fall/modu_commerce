package com.example.commerce.api.point

import com.example.commerce.api.common.userId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 마이 탭의 내 포인트. 잔액·원장은 point-service 가 갖고 있고 여기서는 토큰의 사용자로 대신 조회만 한다. */
@Tag(
    name = "내 포인트 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 API 게이트웨이 /commerce-service/api-public/** 로 부른다(토큰은 게이트웨이가 보고 서비스가 다시 본다). " +
            "모두 계정 토큰(aud modu-commerce) 필요. 잔액·내역은 point-service 에서 대신 읽어 온다.",
)
@RestController
@RequestMapping("/api-public/v1/me/points")
class MyPointController(
    private val pointClient: PointClient,
) {
    @Operation(summary = "포인트 잔액 조회", description = "내 포인트 잔액을 point-service 에서 읽어 돌려준다. point-service 를 못 부르면 503(마이 탭은 포인트 줄만 비운다).")
    @GetMapping
    fun balance(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<MyPointResponse> = ResponseEntity.ok(MyPointResponse(pointClient.balance(jwt.userId()).balance))

    @Operation(
        summary = "포인트 내역 조회",
        description = "내 포인트 적립·사용 내역을 point-service 에서 한 페이지 읽어 돌려준다. point-service 를 못 부르면 503.",
    )
    @GetMapping("/history")
    fun history(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PointHistoryPage> = ResponseEntity.ok(pointClient.history(jwt.userId(), maxOf(page, 0), size.coerceIn(1, 100)))
}

data class MyPointResponse(
    val balance: Long,
)
