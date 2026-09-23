package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Review
import com.example.commerce.application.domain.entity.ReviewSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/** 별점별 개수 한 줄. */
data class RatingCount(
    val rating: Int,
    val count: Long,
)

interface ReviewCustomRepository {
    /** 앱 상품 리뷰 목록. 숨김 제외. */
    fun findVisiblePage(
        productId: Long,
        sort: ReviewSort,
        pageable: Pageable,
    ): Page<Review>

    /** 앱 상품 리뷰 별점 분포(노출 리뷰만). 없는 별점은 빠진다. */
    fun countByRating(productId: Long): List<RatingCount>

    /** 백오피스 목록. 내용·상품명·작성자 이름·이메일 부분 일치, 별점, 숨김 여부, 상품으로 거른다. 최신부터. */
    fun searchAdminPage(
        keyword: String?,
        rating: Int?,
        hidden: Boolean?,
        productId: Long?,
        pageable: Pageable,
    ): Page<Review>
}

interface ReviewRoRepository :
    RoRepository<Review, Long>,
    ReviewCustomRepository {
    fun findById(id: Long): Review?

    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Review?

    fun findAllByUserIdOrderByIdDesc(
        userId: String,
        pageable: Pageable,
    ): Page<Review>

    fun findAllByOrderItemIdIn(orderItemIds: Collection<Long>): List<Review>

    fun findByOrderItemId(orderItemId: Long): Review?
}
