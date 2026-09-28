package com.example.commerce.api.review

import com.example.commerce.api.common.PageResponse
import com.example.commerce.application.usecase.result.ReviewResult
import com.example.commerce.application.usecase.review.DeleteAdminReviewUseCase
import com.example.commerce.application.usecase.review.GetAdminReviewUseCase
import com.example.commerce.application.usecase.review.SearchAdminReviewsUseCase
import com.example.commerce.application.usecase.review.SetReviewHiddenUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 백오피스 리뷰 관리: 검색, 단건, 숨김/노출, 삭제. 작성자 이름·이메일은 그대로 보인다. */
@Tag(
    name = "리뷰 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/reviews")
class AdminReviewController(
    private val searchAdminReviewsUseCase: SearchAdminReviewsUseCase,
    private val getAdminReviewUseCase: GetAdminReviewUseCase,
    private val setReviewHiddenUseCase: SetReviewHiddenUseCase,
    private val deleteAdminReviewUseCase: DeleteAdminReviewUseCase,
) {
    @Operation(
        summary = "리뷰 목록 검색",
        description = "숨긴 리뷰까지 최신 순(id 내림차순)으로 한 페이지 돌려준다. 삭제한 리뷰는 나오지 않는다. 작성자 이름·이메일이 그대로 보인다.",
    )
    @GetMapping
    fun reviews(
        @Parameter(description = "검색어. 내용·상품명·작성자 이름·이메일 부분 일치(대소문자 무시)", example = "배송")
        @RequestParam(required = false) q: String?,
        @Parameter(description = "별점(1~5)이 정확히 같은 리뷰만", example = "5")
        @RequestParam(required = false) rating: Int?,
        @Parameter(description = "true 면 숨긴 리뷰만, false 면 보이는 리뷰만. 없으면 전체", example = "false")
        @RequestParam(required = false) hidden: Boolean?,
        @Parameter(description = "상품 id", example = "1")
        @RequestParam(required = false) productId: Long?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 15, 1~100 으로 자른다", example = "15")
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<ReviewResult>> =
        ResponseEntity.ok(
            PageResponse.from(searchAdminReviewsUseCase.execute(q, rating, hidden, productId, page, size)) {
                it
            },
        )

    @Operation(summary = "리뷰 상세 조회", description = "숨김 여부·사유와 작성자 정보까지 돌려준다. 없거나 삭제한 리뷰면 404.")
    @GetMapping("/{id}")
    fun review(
        @Parameter(description = "리뷰 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(getAdminReviewUseCase.execute(id))

    @Operation(
        summary = "리뷰 숨김·노출",
        description =
            "숨기면 앱에서 안 보이고 상품 별점·리뷰 수 집계에서 빠진다. 다시 노출하면 집계에 돌아온다. " +
                "이미 그 상태면 아무것도 바꾸지 않는다. 없는 리뷰면 404.",
    )
    @PatchMapping("/{id}/hidden")
    fun setHidden(
        @Parameter(description = "리뷰 id", example = "1")
        @PathVariable id: Long,
        @Valid @RequestBody request: SetHiddenRequest,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(setReviewHiddenUseCase.execute(id, requireNotNull(request.hidden), request.reason))

    @Operation(
        summary = "리뷰 삭제",
        description = "소프트 삭제하고 204 를 돌려준다. 보이던 리뷰면 상품 별점·리뷰 수 집계에서 뺀다. 없는 리뷰면 404.",
    )
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "리뷰 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteAdminReviewUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
