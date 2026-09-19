package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductSort
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.QProduct.product
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class ProductCustomRepositoryImpl(
    @Qualifier("roQueryFactory") private val queryFactory: JPAQueryFactory,
) : ProductCustomRepository {
    override fun search(keyword: String): List<Product> =
        queryFactory
            .selectFrom(product)
            .where(matches(keyword))
            .orderBy(product.id.asc())
            .fetch()

    override fun searchAdminPage(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        pageable: Pageable,
    ): Page<Product> {
        val conditions =
            listOfNotNull(
                keyword?.trim()?.takeIf { it.isNotEmpty() }?.let(::matches),
                categoryId?.let { product.category().id.eq(it) },
                status?.let { product.status.eq(it) },
            )
        return page(conditions, listOf(product.id.desc()), pageable)
    }

    override fun searchAppPage(
        categoryIds: Collection<Long>?,
        keyword: String?,
        sort: ProductSort,
        pageable: Pageable,
    ): Page<Product> {
        if (categoryIds != null && categoryIds.isEmpty()) return PageImpl(emptyList(), pageable, 0)
        val conditions =
            listOfNotNull(
                product.status.eq(ProductStatus.SELLING),
                categoryIds?.let { product.category().id.`in`(it) },
                keyword?.trim()?.takeIf { it.isNotEmpty() }?.let(::matches),
            )
        val order =
            when (sort) {
                ProductSort.LATEST -> listOf(product.id.desc())
                ProductSort.PRICE_ASC -> listOf(product.price.asc(), product.id.desc())
                ProductSort.PRICE_DESC -> listOf(product.price.desc(), product.id.desc())
                ProductSort.POPULAR -> listOf(product.wishCount.desc(), product.id.desc())
            }
        return page(conditions, order, pageable)
    }

    private fun page(
        conditions: List<BooleanExpression>,
        order: List<OrderSpecifier<*>>,
        pageable: Pageable,
    ): Page<Product> {
        val content =
            queryFactory
                .selectFrom(product)
                .where(*conditions.toTypedArray())
                .orderBy(*order.toTypedArray())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()
        val total =
            queryFactory
                .select(product.count())
                .from(product)
                .where(*conditions.toTypedArray())
                .fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    private fun matches(keyword: String): BooleanExpression = product.name.contains(keyword).or(product.description.contains(keyword))
}
