package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.QProduct.product
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.beans.factory.annotation.Qualifier

class ProductCustomRepositoryImpl(
    @Qualifier("roQueryFactory") private val queryFactory: JPAQueryFactory,
) : ProductCustomRepository {
    override fun search(keyword: String): List<Product> =
        queryFactory
            .selectFrom(product)
            .where(product.name.contains(keyword).or(product.description.contains(keyword)))
            .orderBy(product.id.asc())
            .fetch()
}
