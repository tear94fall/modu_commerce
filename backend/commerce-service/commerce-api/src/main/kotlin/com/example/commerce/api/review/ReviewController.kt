package com.example.commerce.api.review

import com.example.commerce.api.common.CustomerRequired
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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(
    name = "리뷰 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). 모두 계정 토큰(aud modu-commerce) 필요. " +
            "상품 리뷰 목록·요약은 커머스 가입 전에도 보고, 나머지는 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
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
    @Operation(
        summary = "상품 리뷰 목록 조회",
        description = "숨김·삭제되지 않은 리뷰를 한 페이지 돌려준다. 남의 리뷰는 작성자 이름을 가리고, 내 리뷰는 mine=true 에 이름이 그대로 나온다.",
    )
    @GetMapping("/products/{productId}/reviews")
    fun productReviews(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "상품 id", example = "1")
        @PathVariable productId: Long,
        @Parameter(description = "정렬: latest(최신순, 기본), high(별점 높은 순), low(별점 낮은 순). 모르는 값은 latest", example = "latest")
        @RequestParam(required = false) sort: String?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 10, 1~100 으로 자른다", example = "10")
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<PageResponse<ReviewResult>> =
        ResponseEntity.ok(
            PageResponse.from(getProductReviewsUseCase.execute(jwt.userId(), productId, ReviewSort.of(sort), page, size)) {
                it
            },
        )

    @Operation(
        summary = "상품 리뷰 요약 조회",
        description = "리뷰 수·평균 별점·별점(1~5)별 개수를 돌려준다. 숨긴 리뷰는 집계에서 빠진다. 없는 상품이면 404.",
    )
    @GetMapping("/products/{productId}/reviews/summary")
    fun summary(
        @Parameter(description = "상품 id", example = "1")
        @PathVariable productId: Long,
    ): ResponseEntity<ReviewSummaryResult> = ResponseEntity.ok(getReviewSummaryUseCase.execute(productId))

    /** 리뷰 쓰기 화면이 먼저 부른다: 주문 줄 정보와 쓸 수 있는지. 남의 주문 줄은 404. */
    @Operation(
        summary = "리뷰 쓸 주문 상품 조회",
        description =
            "리뷰 쓰기 화면이 먼저 부른다. 주문 줄의 상품·옵션 정보와 쓸 수 있는지(reviewable: 아직 안 썼고 취소되지 않은 주문), " +
                "이미 썼으면 그 리뷰 id 를 돌려준다. 내 주문 줄이 아니면 404. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @GetMapping("/reviews/targets/{orderItemId}")
    fun target(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "주문 줄(주문 상품) id", example = "20")
        @PathVariable orderItemId: Long,
    ): ResponseEntity<ReviewTargetResult> = ResponseEntity.ok(getReviewTargetUseCase.execute(jwt.userId(), orderItemId))

    @Operation(
        summary = "내 리뷰 목록 조회",
        description = "내가 쓴 리뷰(관리자가 숨긴 것 포함, 삭제한 것 제외)를 최근 순으로 한 페이지 돌려준다. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @GetMapping("/me/reviews")
    fun myReviews(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ReviewResult>> =
        ResponseEntity.ok(
            PageResponse.from(getMyReviewsUseCase.execute(jwt.userId(), page, size)) {
                it
            },
        )

    @Operation(
        summary = "내 리뷰 조회",
        description = "수정 화면용으로 내 리뷰 하나를 돌려준다. 내 리뷰가 아니거나 없으면 404. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @GetMapping("/reviews/{id}")
    fun myReview(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "리뷰 id", example = "7")
        @PathVariable id: Long,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(getMyReviewUseCase.execute(jwt.userId(), id))

    @Operation(
        summary = "리뷰 쓰기",
        description =
            "내 주문 줄 하나에 리뷰를 하나 쓰고 201 과 Location 을 돌려준다. 상품 리뷰 수·별점 캐시도 올린다. " +
                "내 주문 줄이 아니면 404, 이미 썼거나 취소된 주문이거나 별점·길이가 틀리면 400. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PostMapping("/reviews")
    fun write(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: WriteReviewRequest,
    ): ResponseEntity<ReviewResult> {
        val created = writeReviewUseCase.execute(jwt.userId(), request.toCommand())
        return ResponseEntity.created(URI.create("/api/v1/reviews/${created.id}")).body(created)
    }

    @Operation(
        summary = "리뷰 수정",
        description =
            "내 리뷰의 별점·내용을 바꾼다. 보이는 리뷰면 상품 별점 캐시도 맞춘다. 내 리뷰가 아니면 404, 별점·길이가 틀리면 400. " +
                "모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PutMapping("/reviews/{id}")
    fun edit(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "리뷰 id", example = "7")
        @PathVariable id: Long,
        @Valid @RequestBody request: EditReviewRequest,
    ): ResponseEntity<ReviewResult> = ResponseEntity.ok(editReviewUseCase.execute(jwt.userId(), id, request.toCommand()))

    @Operation(
        summary = "리뷰 삭제",
        description = "내 리뷰를 지우고(소프트 삭제) 204. 상품 리뷰 수·별점 캐시에서도 뺀다. 내 리뷰가 아니면 404. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @DeleteMapping("/reviews/{id}")
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "리뷰 id", example = "7")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteReviewUseCase.execute(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }
}
