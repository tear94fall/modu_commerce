package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.QReview.review
import com.example.commerce.application.domain.entity.Review
import com.example.commerce.application.domain.entity.ReviewSort
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class ReviewCustomRepositoryImpl(
    @Qualifier("roQueryFactory") private val queryFactory: JPAQueryFactory,
) : ReviewCustomRepository {
    override fun findVisiblePage(
        productId: Long,
        sort: ReviewSort,
        pageable: Pageable,
    ): Page<Review> {
        val orders: List<OrderSpecifier<*>> =
            when (sort) {
                ReviewSort.LATEST -> listOf(review.id.desc())
                ReviewSort.HIGH -> listOf(review.rating.desc(), review.id.desc())
                ReviewSort.LOW -> listOf(review.rating.asc(), review.id.desc())
            }
        return page(listOf(visibleOf(productId)), orders, pageable)
    }

    override fun countByRating(productId: Long): List<RatingCount> =
        queryFactory
            .select(review.rating, review.count())
            .from(review)
            .where(visibleOf(productId))
            .groupBy(review.rating)
            .fetch()
            .map { RatingCount(requireNotNull(it.get(review.rating)), it.get(review.count()) ?: 0L) }

    override fun searchAdminPage(
        keyword: String?,
        rating: Int?,
        hidden: Boolean?,
        productId: Long?,
        pageable: Pageable,
    ): Page<Review> {
        val conditions =
            listOfNotNull(
                keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { k ->
                    review.content
                        .containsIgnoreCase(k)
                        .or(review.productName.containsIgnoreCase(k))
                        .or(review.authorName.containsIgnoreCase(k))
                        .or(review.authorEmail.containsIgnoreCase(k))
                },
                rating?.let { review.rating.eq(it) },
                hidden?.let { review.hidden.eq(it) },
                productId?.let { review.product().id.eq(it) },
            )
        return page(conditions, listOf(review.id.desc()), pageable)
    }

    private fun visibleOf(productId: Long): BooleanExpression =
        review
            .product()
            .id
            .eq(productId)
            .and(review.hidden.isFalse)

    private fun page(
        conditions: List<BooleanExpression>,
        orders: List<OrderSpecifier<*>>,
        pageable: Pageable,
    ): Page<Review> {
        val content =
            queryFactory
                .selectFrom(review)
                .where(*conditions.toTypedArray())
                .orderBy(*orders.toTypedArray())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()
        val total =
            queryFactory
                .select(review.count())
                .from(review)
                .where(*conditions.toTypedArray())
                .fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }
}
