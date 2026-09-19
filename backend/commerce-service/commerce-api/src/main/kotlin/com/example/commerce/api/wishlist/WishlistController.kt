package com.example.commerce.api.wishlist

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.api.product.response.ProductSummaryResponse
import com.example.commerce.application.usecase.wishlist.AddWishUseCase
import com.example.commerce.application.usecase.wishlist.GetWishlistUseCase
import com.example.commerce.application.usecase.wishlist.RemoveWishUseCase
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
@RestController
@RequestMapping("/api/v1/wishlist")
class WishlistController(
    private val getWishlistUseCase: GetWishlistUseCase,
    private val addWishUseCase: AddWishUseCase,
    private val removeWishUseCase: RemoveWishUseCase,
) {
    @GetMapping
    fun wishlist(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ProductSummaryResponse>> =
        ResponseEntity.ok(PageResponse.from(getWishlistUseCase.execute(jwt.userId(), page, size), ProductSummaryResponse::from))

    @PostMapping("/{productId}")
    fun add(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable productId: Long,
    ): ResponseEntity<Void> {
        addWishUseCase.execute(jwt.userId(), productId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{productId}")
    fun remove(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable productId: Long,
    ): ResponseEntity<Void> {
        removeWishUseCase.execute(jwt.userId(), productId)
        return ResponseEntity.noContent().build()
    }
}
