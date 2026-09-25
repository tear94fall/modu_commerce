package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.OrderItem
import com.example.commerce.application.domain.entity.Review
import com.example.commerce.application.domain.entity.ReviewSort
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.ReviewRoRepository
import com.example.commerce.application.domain.repository.rw.OrderItemRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.domain.repository.rw.ReviewRwRepository
import com.example.commerce.application.member.MemberLookup
import com.example.commerce.application.usecase.command.EditReviewCommand
import com.example.commerce.application.usecase.command.WriteReviewCommand
import com.example.commerce.application.usecase.result.ReviewSummaryResult
import com.example.commerce.application.usecase.result.ReviewTargetResult
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class ReviewQueryService(
    private val reviewRoRepository: ReviewRoRepository,
    private val orderRoRepository: OrderRoRepository,
    private val productQueryService: ProductQueryService,
) {
    fun productPage(
        productId: Long,
        sort: ReviewSort,
        page: Int,
        size: Int,
    ): Page<Review> = reviewRoRepository.findVisiblePage(productId, sort, pageOf(page, size))

    fun summary(productId: Long): ReviewSummaryResult {
        val product = productQueryService.findProduct(productId)
        return ReviewSummaryResult.of(product.reviewCount, product.ratingAverage(), reviewRoRepository.countByRating(productId))
    }

    fun myPage(
        userId: String,
        page: Int,
        size: Int,
    ): Page<Review> = reviewRoRepository.findAllByUserIdOrderByIdDesc(userId, pageOf(page, size))

    fun own(
        userId: String,
        id: Long,
    ): Review = reviewRoRepository.findByIdAndUserId(id, userId) ?: throw notFound(id)

    /** 주문 상세가 줄마다 리뷰 id 를 붙일 때. orderItemId → reviewId. */
    fun reviewIdsOf(orderItemIds: Collection<Long>): Map<Long, Long> =
        if (orderItemIds.isEmpty()) {
            emptyMap()
        } else {
            reviewRoRepository.findAllByOrderItemIdIn(orderItemIds).associate { requireNotNull(it.orderItem.id) to requireNotNull(it.id) }
        }

    /** 내 주문 줄이어야 한다. 남의 것·없는 것은 404 로 감춘다. */
    fun target(
        userId: String,
        orderItemId: Long,
    ): ReviewTargetResult {
        val order =
            orderRoRepository.findByItemIdAndUserId(orderItemId, userId)
                ?: throw EntityNotFoundException("id: $orderItemId 에 해당하는 주문 상품이 없습니다.")
        val item = order.items.first { it.id == orderItemId }
        val existing = reviewRoRepository.findByOrderItemId(orderItemId)
        return ReviewTargetResult(
            orderItemId = orderItemId,
            productId = requireNotNull(item.product.id),
            productName = item.productName,
            optionLabel = item.optionLabel,
            imageUrl = item.imageUrl,
            reviewable = existing == null && Review.canReview(order),
            reviewId = existing?.id,
        )
    }

    fun adminPage(
        keyword: String?,
        rating: Int?,
        hidden: Boolean?,
        productId: Long?,
        page: Int,
        size: Int,
    ): Page<Review> = reviewRoRepository.searchAdminPage(keyword, rating, hidden, productId, pageOf(page, size))

    fun find(id: Long): Review = reviewRoRepository.findById(id) ?: throw notFound(id)

    private fun pageOf(
        page: Int,
        size: Int,
    ) = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, ProductQueryService.MAX_PAGE_SIZE))

    companion object {
        fun notFound(id: Long) = EntityNotFoundException("id: $id 에 해당하는 리뷰가 없습니다.")
    }
}

/**
 * 리뷰 쓰기·수정·삭제와 관리자 숨김. 상품의 리뷰 수·별점 합 캐시를 같이 맞춘다(숨김 리뷰는 집계에서 뺀다).
 * 작성자 이름·이메일은 쓰는 순간 member-service 에서 받아 복사한다(조회 실패는 null).
 */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class ReviewCommandService(
    private val reviewRwRepository: ReviewRwRepository,
    private val orderItemRwRepository: OrderItemRwRepository,
    private val productRwRepository: ProductRwRepository,
    private val memberLookup: MemberLookup,
) {
    fun write(
        userId: String,
        command: WriteReviewCommand,
    ): Review {
        val item = ownItem(userId, command.orderItemId)
        require(Review.canReview(item.order)) { "취소된 주문의 상품에는 리뷰를 쓸 수 없습니다." }
        require(!reviewRwRepository.existsByOrderItemId(command.orderItemId)) { "이미 리뷰를 쓴 상품입니다." }
        val member = runCatching { memberLookup.find(userId) }.getOrNull()
        val product = productRwRepository.findById(requireNotNull(item.product.id)).orElseThrow { EntityNotFoundException("판매가 끝난 상품입니다.") }
        val review =
            Review(
                orderItem = item,
                product = product,
                userId = userId,
                authorName = member?.username,
                authorEmail = member?.email,
                productName = item.productName,
                optionLabel = item.optionLabel,
                productImageUrl = item.imageUrl,
                rating = command.rating,
                content = command.content,
            )
        val saved = reviewRwRepository.saveAndFlush(review)
        product.addRating(saved.rating)
        logger.info { "review ${saved.id} written by $userId on product ${product.id}: ${saved.rating}점" }
        return saved
    }

    fun edit(
        userId: String,
        id: Long,
        command: EditReviewCommand,
    ): Review {
        val review = reviewRwRepository.findByIdAndUserId(id, userId) ?: throw ReviewQueryService.notFound(id)
        val before = review.rating
        review.edit(command.rating, command.content)
        if (review.isVisible()) review.product.replaceRating(before, review.rating)
        return review
    }

    fun delete(
        userId: String,
        id: Long,
    ) {
        val review = reviewRwRepository.findByIdAndUserId(id, userId) ?: throw ReviewQueryService.notFound(id)
        remove(review)
    }

    fun adminDelete(id: Long) {
        val review = reviewRwRepository.findById(id).orElse(null) ?: throw ReviewQueryService.notFound(id)
        remove(review)
    }

    fun setHidden(
        id: Long,
        hidden: Boolean,
        reason: String?,
    ): Review {
        val review = reviewRwRepository.findById(id).orElse(null) ?: throw ReviewQueryService.notFound(id)
        if (hidden == review.hidden) return review
        if (hidden) {
            review.hide(reason)
            review.product.removeRating(review.rating)
        } else {
            review.unhide()
            review.product.addRating(review.rating)
        }
        return review
    }

    private fun remove(review: Review) {
        if (review.isVisible()) review.product.removeRating(review.rating)
        review.delete()
    }

    private fun ownItem(
        userId: String,
        orderItemId: Long,
    ): OrderItem {
        val item = orderItemRwRepository.findById(orderItemId).orElse(null)
        if (item == null || item.order.userId != userId) throw EntityNotFoundException("id: $orderItemId 에 해당하는 주문 상품이 없습니다.")
        return item
    }
}
