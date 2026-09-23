package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Review
import com.example.commerce.application.domain.repository.ro.RatingCount
import java.time.LocalDateTime

/**
 * 리뷰 한 건. 앱 목록은 작성자 이름을 가리고(임*섭) 이메일을 빼며, 본인 것과 백오피스는 그대로 준다.
 * [mine] 은 요청한 사용자가 쓴 리뷰인지(앱 목록에서 수정·삭제 버튼을 보일지).
 */
data class ReviewResult(
    val id: Long,
    val productId: Long,
    val productName: String,
    val optionLabel: String,
    val productImageUrl: String?,
    val orderItemId: Long,
    val userId: String?,
    val authorName: String,
    val authorEmail: String?,
    val rating: Int,
    val content: String,
    val hidden: Boolean,
    val hiddenReason: String?,
    val mine: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    companion object {
        /** 앱 상품 리뷰 목록용. 남의 리뷰는 이름을 가린다. */
        fun forViewer(
            r: Review,
            viewerId: String?,
        ): ReviewResult {
            val mine = viewerId != null && r.userId == viewerId
            return of(r, mine, exposeAuthor = mine)
        }

        /** 본인 리뷰·백오피스용. 이름·이메일·userId 를 그대로 준다. */
        fun full(r: Review) = of(r, mine = true, exposeAuthor = true)

        private fun of(
            r: Review,
            mine: Boolean,
            exposeAuthor: Boolean,
        ) = ReviewResult(
            id = requireNotNull(r.id),
            productId = requireNotNull(r.product.id),
            productName = r.productName,
            optionLabel = r.optionLabel,
            productImageUrl = r.productImageUrl,
            orderItemId = requireNotNull(r.orderItem.id),
            userId = if (exposeAuthor) r.userId else null,
            authorName = if (exposeAuthor) r.authorName?.takeIf { it.isNotBlank() } ?: "모두 회원" else Review.maskName(r.authorName),
            authorEmail = if (exposeAuthor) r.authorEmail else null,
            rating = r.rating,
            content = r.content,
            hidden = r.hidden,
            hiddenReason = r.hiddenReason,
            mine = mine,
            createdAt = r.createdAt,
            updatedAt = r.updatedAt,
        )
    }
}

/** 상품 리뷰 요약. [distribution] 은 5점부터 1점까지 항상 다섯 줄. */
data class ReviewSummaryResult(
    val count: Long,
    val average: Double,
    val distribution: List<RatingCount>,
) {
    companion object {
        fun of(
            count: Long,
            average: Double,
            counts: List<RatingCount>,
        ): ReviewSummaryResult {
            val byRating = counts.associate { it.rating to it.count }
            return ReviewSummaryResult(count, average, (5 downTo 1).map { RatingCount(it, byRating[it] ?: 0L) })
        }
    }
}

/** 리뷰 쓰기 화면이 먼저 묻는 것: 이 주문 줄에 리뷰를 쓸 수 있나, 이미 썼다면 어느 리뷰인가. */
data class ReviewTargetResult(
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val optionLabel: String,
    val imageUrl: String?,
    val reviewable: Boolean,
    val reviewId: Long?,
)
