package com.example.commerce.api.product

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.product.request.ProductRequest
import com.example.commerce.api.product.response.AdminProductSummaryResponse
import com.example.commerce.api.product.response.ProductDetailResponse
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.usecase.product.CreateProductUseCase
import com.example.commerce.application.usecase.product.DeleteProductUseCase
import com.example.commerce.application.usecase.product.GetAdminProductUseCase
import com.example.commerce.application.usecase.product.SearchAdminProductsUseCase
import com.example.commerce.application.usecase.product.UpdateProductUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 백오피스가 게이트웨이(/commerce-service/api-admin 이하)를 거쳐 부른다. 권한은 SecurityConfig 의 admin 체인이 본다. */
@Tag(
    name = "상품 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/products")
class AdminProductController(
    private val searchAdminProductsUseCase: SearchAdminProductsUseCase,
    private val getAdminProductUseCase: GetAdminProductUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
) {
    @Operation(
        summary = "상품 목록 검색",
        description = "숨긴 상품까지 포함해 최신 등록 순(id 내림차순)으로 한 페이지 돌려준다. 조건을 주지 않으면 전체.",
    )
    @GetMapping
    fun products(
        @Parameter(description = "검색어(상품명·설명 부분 일치). 앞뒤 공백은 무시", example = "텀블러")
        @RequestParam(required = false) q: String?,
        @Parameter(description = "카테고리 id. 그 카테고리에 바로 속한 상품만(하위 카테고리는 포함하지 않는다)", example = "3")
        @RequestParam(required = false) categoryId: Long?,
        @Parameter(description = "판매 상태: SELLING(판매 중), HIDDEN(숨김). 없으면 전체", example = "SELLING")
        @RequestParam(required = false) status: ProductStatus?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 15, 1~100 으로 자른다", example = "15")
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<AdminProductSummaryResponse>> =
        ResponseEntity.ok(
            PageResponse.from(searchAdminProductsUseCase.execute(q, categoryId, status, page, size), AdminProductSummaryResponse::from),
        )

    @Operation(
        summary = "상품 상세 조회",
        description = "숨긴 상품도 옵션 그룹·SKU(재고·추가금)·이미지까지 돌려준다. 없거나 삭제한 상품이면 404.",
    )
    @GetMapping("/{id}")
    fun product(
        @Parameter(description = "상품 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<ProductDetailResponse> = ResponseEntity.ok(ProductDetailResponse.from(getAdminProductUseCase.execute(id)))

    @Operation(
        summary = "상품 등록",
        description =
            "상품을 만들고 201 과 상세를 돌려준다. 옵션 그룹(최대 3개)과 SKU 조합을 함께 받는다. " +
                "필드 검증이 틀리면 첫 오류 하나를 400 으로 알리고, 옵션 조합이 맞지 않거나 정가가 판매가보다 낮아도 400. 없는 카테고리면 404.",
    )
    @PostMapping
    fun create(
        @Valid @RequestBody request: ProductRequest,
    ): ResponseEntity<ProductDetailResponse> {
        val created = createProductUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/api-admin/v1/products/${created.id}")).body(ProductDetailResponse.from(created))
    }

    @Operation(
        summary = "상품 수정",
        description =
            "모든 값을 다시 보내 통째로 바꾼다(이미지·옵션·SKU 도 보낸 목록으로 교체). " +
                "없는 상품·카테고리면 404, 검증·옵션 규칙 위반이면 400.",
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "상품 id", example = "1")
        @PathVariable id: Long,
        @Valid @RequestBody request: ProductRequest,
    ): ResponseEntity<ProductDetailResponse> =
        ResponseEntity.ok(ProductDetailResponse.from(updateProductUseCase.execute(id, request.toCommand())))

    @Operation(
        summary = "상품 삭제",
        description = "소프트 삭제하고 204 를 돌려준다. 앱 목록·상세에서 사라지고 지난 주문 내역은 그대로다. 없는 상품이면 404.",
    )
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "상품 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteProductUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
