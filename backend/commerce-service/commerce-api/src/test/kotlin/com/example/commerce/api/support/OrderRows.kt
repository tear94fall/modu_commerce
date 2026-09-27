package com.example.commerce.api.support

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** 등급 테스트용 주문 행을 바로 넣는다(상품 줄 없이 금액·상태·배송 완료 시각만). 시각은 UTC. */
@Component
class OrderRows(
    @Qualifier("rwDataSource") dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val seq = AtomicInteger()

    fun insert(
        userId: String,
        total: Long,
        deliveredAtUtc: LocalDateTime?,
        status: String = "DELIVERED",
        points: Long = 0,
        coupon: Long = 0,
    ): Long {
        val no = "T" + System.nanoTime().toString().takeLast(12) + seq.incrementAndGet()
        val at = Timestamp.valueOf(deliveredAtUtc ?: LocalDateTime.of(2026, 1, 1, 0, 0))
        jdbc.update(
            "insert into orders (created_at, updated_at, address1, order_no, paid_at, payment_method, phone, recipient, status, " +
                "total_amount, user_id, zip_code, point_amount, coupon_discount, earn_status, delivered_at) " +
                "values (?, ?, '서울', ?, ?, 'MOCK', '010-0000-0000', '테스트', ?, ?, ?, '06236', ?, ?, 'NONE', ?)",
            at,
            at,
            no.take(20),
            at,
            status,
            total,
            userId,
            points,
            coupon,
            deliveredAtUtc?.let { Timestamp.valueOf(it) },
        )
        return requireNotNull(jdbc.queryForObject("select id from orders where order_no = ?", Long::class.java, no.take(20)))
    }
}
