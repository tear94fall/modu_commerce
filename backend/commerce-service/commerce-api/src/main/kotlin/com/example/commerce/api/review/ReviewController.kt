package com.example.commerce.api.review

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.application.domain.entity.ReviewSort
import com.example.commerce.application.usecase.result.ReviewResult
import com.example.commerce.application.usecase.result.ReviewSummaryResult
import com.example.commerce.application.usecase.result.ReviewTargetResult
import com.example.commerce.application.usecase.review.DeleteReviewUseCase
import com.example.commerce.application.usecase.review.EditReviewUseCase
import com.example.commerce.application.usecase.review.GetMyReviewUseCase
import com.example.commerce.application.usecase.review.GetMyReviewsUseCase
import com.example.commerce.application.usecase.review.GetProductReviewsUseCase
import com.example.commerce.application.usecase.review.GetReviewSummaryUseCase
import com.example.commerce.application.usecase.review.GetReviewTargetUseCase
import com.example.commerce.application.usecase.review.WriteReviewUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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

/** 앱 리뷰. 상품별 목록·요약은 누구나(로그인 사용자) 보고, 쓰기·수정·삭제는 본인 주문 줄에만. */
@RestController
@RequestMapping("/api/v1")
class ReviewController(
    private val getProductReviewsUseCase: GetProductReviewsUseCase,
    private val getReviewSummaryUseCase: GetReviewSummaryUseCase,
    private val getReviewTargetUseCase: GetReviewTargetUseCase,
    private val getMyReviewsUseCase: GetMyReviewsUseCase,
    private val getMyReviewUseCase: GetMyReviewUseCase,
    private val writeReviewUseCase: WriteReviewUseCase,
    private val editReviewUseCase: EditReviewUseCase,
    private val deleteReviewUseCase: DeleteReviewUseCase,
) {
    @GetMapping("/products/{productId}/reviews")
    fun productReviews(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable productId: Long,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<PageResponse<ReviewResult>> =
        ResponseEntity.ok(
            PageResponse.from(getProductReviewsUseCase.execute(jwt.userId(), productId, ReviewSort.of(sort), page, size)) {
                it
            },
        )

    @GetMapping("/products/{productId}/reviews/summary")
    fun summary(
        @PathVariable productId: Long,
    ): ResponseEntity<ReviewSummaryResult> = ResponseEntity.ok(getReviewSummaryUseCase.execute(productId))

    /** 리뷰 쓰기 화면이 먼저 부른다: 주문 줄 정보와 쓸 수 있는지. 남의 주문 줄은 404. */
    @GetMapping("/reviews/targets/{orderItemId}")
    fun target(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable orderItemId: Long,
    ): ResponseEntity<ReviewTargetResult> = ResponseEntity.ok(getReviewTargetUseCase.execute(jwt.userId(), orderItemId))

    @GetMapping("/me/reviews")
    fun myReviews(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ReviewResult>> =
        ResponseEntity.ok(
            PageResponse.from(getMyReviewsUseCase.execute(jwt.userId(), page, size)) {
                it
            },
        )

    @GetMapping("/reviews/{id}")
    fun myReview(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(getMyReviewUseCase.execute(jwt.userId(), id))

    @PostMapping("/reviews")
    fun write(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: WriteReviewRequest,
    ): ResponseEntity<ReviewResult> {
        val created = writeReviewUseCase.execute(jwt.userId(), request.toCommand())
        return ResponseEntity.created(URI.create("/api/v1/reviews/${created.id}")).body(created)
    }

    @PutMapping("/reviews/{id}")
    fun edit(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @Valid @RequestBody request: EditReviewRequest,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(editReviewUseCase.execute(jwt.userId(), id, request.toCommand()))

    @DeleteMapping("/reviews/{id}")
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteReviewUseCase.execute(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }
}
