package com.example.commerce.application.usecase.review

import com.example.commerce.application.domain.entity.ReviewSort
import com.example.commerce.application.service.ReviewCommandService
import com.example.commerce.application.service.ReviewQueryService
import com.example.commerce.application.usecase.command.EditReviewCommand
import com.example.commerce.application.usecase.command.WriteReviewCommand
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.ReviewResult
import com.example.commerce.application.usecase.result.ReviewSummaryResult
import com.example.commerce.application.usecase.result.ReviewTargetResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetProductReviewsUseCase(
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        viewerId: String?,
        productId: Long,
        sort: ReviewSort,
        page: Int,
        size: Int,
    ): PageResult<ReviewResult> =
        PageResult.from(reviewQueryService.productPage(productId, sort, page, size)) {
            ReviewResult.forViewer(it, viewerId)
        }
}

@Component
class GetReviewSummaryUseCase(
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(productId: Long): ReviewSummaryResult = reviewQueryService.summary(productId)
}

@Component
class GetReviewTargetUseCase(
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        orderItemId: Long,
    ): ReviewTargetResult = reviewQueryService.target(userId, orderItemId)
}

@Component
class GetMyReviewsUseCase(
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        page: Int,
        size: Int,
    ): PageResult<ReviewResult> = PageResult.from(reviewQueryService.myPage(userId, page, size), ReviewResult::full)
}

@Component
class GetMyReviewUseCase(
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        id: Long,
    ): ReviewResult = ReviewResult.full(reviewQueryService.own(userId, id))
}

@Component
class WriteReviewUseCase(
    private val reviewCommandService: ReviewCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        userId: String,
        command: WriteReviewCommand,
    ): ReviewResult = ReviewResult.full(reviewCommandService.write(userId, command))
}

@Component
class EditReviewUseCase(
    private val reviewCommandService: ReviewCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        userId: String,
        id: Long,
        command: EditReviewCommand,
    ): ReviewResult = ReviewResult.full(reviewCommandService.edit(userId, id, command))
}

@Component
class DeleteReviewUseCase(
    private val reviewCommandService: ReviewCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        userId: String,
        id: Long,
    ) = reviewCommandService.delete(userId, id)
}

@Component
class SearchAdminReviewsUseCase(
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        keyword: String?,
        rating: Int?,
        hidden: Boolean?,
        productId: Long?,
        page: Int,
        size: Int,
    ): PageResult<ReviewResult> =
        PageResult.from(reviewQueryService.adminPage(keyword, rating, hidden, productId, page, size), ReviewResult::full)
}

@Component
class GetAdminReviewUseCase(
    private val reviewQueryService: ReviewQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(id: Long): ReviewResult = ReviewResult.full(reviewQueryService.find(id))
}

@Component
class SetReviewHiddenUseCase(
    private val reviewCommandService: ReviewCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        id: Long,
        hidden: Boolean,
        reason: String?,
    ): ReviewResult = ReviewResult.full(reviewCommandService.setHidden(id, hidden, reason))
}

@Component
class DeleteAdminReviewUseCase(
    private val reviewCommandService: ReviewCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(id: Long) = reviewCommandService.adminDelete(id)
}
