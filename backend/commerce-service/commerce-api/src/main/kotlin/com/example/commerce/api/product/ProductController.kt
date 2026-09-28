package com.example.commerce.api.product

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.api.product.response.ProductDetailResponse
import com.example.commerce.api.product.response.ProductSummaryResponse
import com.example.commerce.application.usecase.product.GetProductDetailUseCase
import com.example.commerce.application.usecase.product.GetProductsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "상품 (앱)",
    description = "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). 모두 계정 토큰(aud modu-commerce) 필요. 둘러보기라 커머스 가입 전에도 부를 수 있다.",
)
@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val getProductsUseCase: GetProductsUseCase,
    private val getProductDetailUseCase: GetProductDetailUseCase,
) {
    @Operation(
        summary = "상품 목록 조회",
        description =
            "판매 중인 상품만 한 페이지 돌려준다. 카테고리를 주면 그 아래 소분류까지 포함하고, q 는 상품명·설명에 포함되는지로 찾는다. " +
                "각 상품에 내가 찜했는지(wished)가 붙는다.",
    )
    @GetMapping
    fun products(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "카테고리 id. 하위 카테고리 상품까지 포함한다. 없으면 전체", example = "3")
        @RequestParam(required = false) categoryId: Long?,
        @Parameter(description = "검색어(상품명·설명 부분 일치). 앞뒤 공백은 무시", example = "텀블러")
        @RequestParam(required = false) q: String?,
        @Parameter(
            description = "정렬: latest(최신순, 기본), priceAsc(낮은 가격순), priceDesc(높은 가격순), popular(찜 많은 순). 모르는 값은 latest",
            example = "latest",
        )
        @RequestParam(required = false) sort: String?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ProductSummaryResponse>> =
        ResponseEntity.ok(
            PageResponse.from(getProductsUseCase.execute(jwt.userId(), categoryId, q, sort, page, size), ProductSummaryResponse::from),
        )

    @Operation(
        summary = "상품 상세 조회",
        description = "옵션(SKU)·재고·이미지와 내가 찜했는지를 함께 돌려준다. 없거나 숨긴(판매 중이 아닌) 상품이면 404.",
    )
    @GetMapping("/{id}")
    fun product(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "상품 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<ProductDetailResponse> =
        ResponseEntity.ok(ProductDetailResponse.from(getProductDetailUseCase.execute(jwt.userId(), id)))
}
