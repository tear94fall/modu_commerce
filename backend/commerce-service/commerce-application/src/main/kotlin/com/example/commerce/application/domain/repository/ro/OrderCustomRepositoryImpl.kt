package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.QOrder.order
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class OrderCustomRepositoryImpl(
    @Qualifier("roQueryFactory") private val queryFactory: JPAQueryFactory,
) : OrderCustomRepository {
    override fun searchAdminPage(
        status: OrderStatus?,
        orderNo: String?,
        pageable: Pageable,
    ): Page<Order> {
        val conditions =
            listOfNotNull(
                status?.let { order.status.eq(it) },
                orderNo?.trim()?.takeIf { it.isNotEmpty() }?.let { order.orderNo.containsIgnoreCase(it) },
            )
        val content =
            queryFactory
                .selectFrom(order)
                .where(*conditions.toTypedArray())
                .orderBy(order.id.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()
        val total =
            queryFactory
                .select(order.count())
                .from(order)
                .where(*conditions.toTypedArray())
                .fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }
}
