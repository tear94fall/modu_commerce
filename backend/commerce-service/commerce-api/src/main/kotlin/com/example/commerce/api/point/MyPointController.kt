package com.example.commerce.api.point

import com.example.commerce.api.common.userId
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 마이 탭의 내 포인트. 잔액·원장은 point-service 가 갖고 있고 여기서는 토큰의 사용자로 대신 조회만 한다. */
@RestController
@RequestMapping("/api/v1/me/points")
class MyPointController(
    private val pointClient: PointClient,
) {
    @GetMapping
    fun balance(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<MyPointResponse> = ResponseEntity.ok(MyPointResponse(pointClient.balance(jwt.userId()).balance))

    @GetMapping("/history")
    fun history(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PointHistoryPage> = ResponseEntity.ok(pointClient.history(jwt.userId(), maxOf(page, 0), size.coerceIn(1, 100)))
}

data class MyPointResponse(
    val balance: Long,
)
