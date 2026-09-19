package com.example.commerce.api.product

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.api.product.response.ProductDetailResponse
import com.example.commerce.api.product.response.ProductSummaryResponse
import com.example.commerce.application.usecase.product.GetProductDetailUseCase
import com.example.commerce.application.usecase.product.GetProductsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val getProductsUseCase: GetProductsUseCase,
    private val getProductDetailUseCase: GetProductDetailUseCase,
) {
    @GetMapping
    fun products(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ProductSummaryResponse>> =
        ResponseEntity.ok(
            PageResponse.from(getProductsUseCase.execute(jwt.userId(), categoryId, q, sort, page, size), ProductSummaryResponse::from),
        )

    @GetMapping("/{id}")
    fun product(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<ProductDetailResponse> =
        ResponseEntity.ok(ProductDetailResponse.from(getProductDetailUseCase.execute(jwt.userId(), id)))
}
