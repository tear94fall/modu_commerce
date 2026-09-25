package com.example.commerce.api.review

import com.example.commerce.api.common.PageResponse
import com.example.commerce.application.usecase.result.ReviewResult
import com.example.commerce.application.usecase.review.DeleteAdminReviewUseCase
import com.example.commerce.application.usecase.review.GetAdminReviewUseCase
import com.example.commerce.application.usecase.review.SearchAdminReviewsUseCase
import com.example.commerce.application.usecase.review.SetReviewHiddenUseCase
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
@RestController
@RequestMapping("/api-admin/v1/reviews")
class AdminReviewController(
    private val searchAdminReviewsUseCase: SearchAdminReviewsUseCase,
    private val getAdminReviewUseCase: GetAdminReviewUseCase,
    private val setReviewHiddenUseCase: SetReviewHiddenUseCase,
    private val deleteAdminReviewUseCase: DeleteAdminReviewUseCase,
) {
    @GetMapping
    fun reviews(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) rating: Int?,
        @RequestParam(required = false) hidden: Boolean?,
        @RequestParam(required = false) productId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<ReviewResult>> =
        ResponseEntity.ok(
            PageResponse.from(searchAdminReviewsUseCase.execute(q, rating, hidden, productId, page, size)) {
                it
            },
        )

    @GetMapping("/{id}")
    fun review(
        @PathVariable id: Long,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(getAdminReviewUseCase.execute(id))

    @PatchMapping("/{id}/hidden")
    fun setHidden(
        @PathVariable id: Long,
        @Valid @RequestBody request: SetHiddenRequest,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(setReviewHiddenUseCase.execute(id, requireNotNull(request.hidden), request.reason))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteAdminReviewUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
