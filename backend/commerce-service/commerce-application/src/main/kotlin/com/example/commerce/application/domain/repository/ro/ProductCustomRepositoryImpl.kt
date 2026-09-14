package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.QProduct.product
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

    override fun searchPage(
        keyword: String?,
        pageable: Pageable,
    ): Page<Product> {
        val condition = keyword?.trim()?.takeIf { it.isNotEmpty() }?.let(::matches)
        val content =
            queryFactory
                .selectFrom(product)
                .where(condition)
                .orderBy(product.id.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()
        val total =
            queryFactory
                .select(product.count())
                .from(product)
                .where(condition)
                .fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    private fun matches(keyword: String): BooleanExpression = product.name.contains(keyword).or(product.description.contains(keyword))
}
