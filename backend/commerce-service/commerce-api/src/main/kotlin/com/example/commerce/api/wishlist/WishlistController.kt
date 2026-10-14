package com.example.commerce.api.wishlist

import com.example.commerce.api.common.CustomerRequired
import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.api.product.response.ProductSummaryResponse
import com.example.commerce.application.usecase.wishlist.AddWishUseCase
import com.example.commerce.application.usecase.wishlist.GetWishlistUseCase
import com.example.commerce.application.usecase.wishlist.RemoveWishUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 찜. 추가·해제는 멱등이라 둘 다 204. */
@Tag(
    name = "찜 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 API 게이트웨이 /commerce-service/api-public/** 로 부른다(토큰은 게이트웨이가 보고 서비스가 다시 본다). " +
            "모두 계정 토큰(aud modu-commerce) 필요. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
@CustomerRequired
@RestController
@RequestMapping("/api-public/v1/wishlist")
class WishlistController(
    private val getWishlistUseCase: GetWishlistUseCase,
    private val addWishUseCase: AddWishUseCase,
    private val removeWishUseCase: RemoveWishUseCase,
) {
    @Operation(
        summary = "찜 목록 조회",
        description = "내가 찜한 상품을 최근 찜한 순으로 한 페이지 돌려준다. 숨긴 상품은 목록에서 빠진다(페이지 개수가 size 보다 적을 수 있다).",
    )
    @GetMapping
    fun wishlist(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ProductSummaryResponse>> =
        ResponseEntity.ok(PageResponse.from(getWishlistUseCase.execute(jwt.userId(), page, size), ProductSummaryResponse::from))

    @Operation(
        summary = "찜하기",
        description = "상품을 찜하고 204. 멱등이라 이미 찜했어도 204. 상품의 찜 수도 함께 올린다. 없거나 삭제된 상품이면 404.",
    )
    @PostMapping("/{productId}")
    fun add(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "상품 id", example = "1")
        @PathVariable productId: Long,
    ): ResponseEntity<Void> {
        addWishUseCase.execute(jwt.userId(), productId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "찜 해제",
        description = "찜을 풀고 204. 멱등이라 찜하지 않았어도 204. 상품의 찜 수도 함께 내린다. 없거나 삭제된 상품이면 404.",
    )
    @DeleteMapping("/{productId}")
    fun remove(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "상품 id", example = "1")
        @PathVariable productId: Long,
    ): ResponseEntity<Void> {
        removeWishUseCase.execute(jwt.userId(), productId)
        return ResponseEntity.noContent().build()
    }
}
