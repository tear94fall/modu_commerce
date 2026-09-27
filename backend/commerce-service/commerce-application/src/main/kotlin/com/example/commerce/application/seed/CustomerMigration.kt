package com.example.commerce.application.seed

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.domain.repository.rw.TierRwRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import javax.sql.DataSource

/**
 * 고객·등급 도입에 필요한 기동 시 작업. 모두 멱등이라 매번 돌아도 된다.
 *
 * 1. (MySQL 만) ddl-auto=update 가 못 하는 스키마 변경: user_coupons 의 옛 유니크(coupon_id, user_id)를 지우고
 *    source enum 에 TIER 를 넣는다. H2(테스트)는 스키마를 새로 만들므로 건너뛴다.
 * 2. 등급 4개가 없으면 넣는다.
 * 3. 재기동으로 끊긴 RUNNING 산정을 FAILED 로 닫는다.
 * 4. 배송 완료 주문의 delivered_at 을 updated_at 으로 채운다.
 * 5. 기존 데이터(주문·찜·배송지·장바구니·리뷰·푸시·쿠폰·출석)에 있는 회원을 고객으로 옮긴다(migrated, 동의 전, 가장 낮은 등급).
 */
@Component
@Order(10)
class CustomerMigration(
    private val tierRwRepository: TierRwRepository,
    @Qualifier("rwDataSource") private val dataSource: DataSource,
    private val clock: Clock,
) : ApplicationRunner {
    private val jdbc = JdbcTemplate(dataSource)

    override fun run(args: ApplicationArguments) {
        if (isMySql()) fixMySqlSchema()
        seedTiers()
        closeStaleRuns()
        val delivered = jdbc.update("update orders set delivered_at = updated_at where status = 'DELIVERED' and delivered_at is null")
        if (delivered > 0) logger.info { "orders.delivered_at backfilled for $delivered order(s)" }
        backfillCustomers()
    }

    /** 등급이 하나도 없을 때만 기본 4개를 넣는다. */
    fun seedTiers() {
        if (tierRwRepository.count() > 0) return
        tierRwRepository.saveAllAndFlush(defaultTiers())
        logger.info { "commerce tiers seeded" }
    }

    fun closeStaleRuns() {
        val closed =
            jdbc.update(
                "update commerce_tier_runs set status = 'FAILED', finished_at = ?, message = ? where status = 'RUNNING'",
                Timestamp.valueOf(TierPeriods.utcNow(clock)),
                "서버 재기동으로 중단됨",
            )
        if (closed > 0) logger.warn { "closed $closed stale RUNNING tier run(s)" }
    }

    /** 기존 데이터의 회원을 고객으로 옮긴다. 이미 있는 고객은 건드리지 않는다. 넣은 수를 돌려준다. */
    fun backfillCustomers(): Int {
        val lowest = tierRwRepository.findAllByOrderBySortOrderAsc().minByOrNull { it.minAmount }?.code ?: return 0
        val firstSeen =
            jdbc.query(
                "select user_id, min(t) as first_at from (" + SOURCES.joinToString(" union all ") + ") u group by user_id",
            ) { rs, _ -> rs.getString(1) to rs.getTimestamp(2) }
        if (firstSeen.isEmpty()) return 0
        val existing = jdbc.queryForList("select user_id from commerce_customers", String::class.java).toSet()
        val now = Timestamp.valueOf(TierPeriods.utcNow(clock))
        val rows = firstSeen.filter { it.first !in existing }.map { (userId, at) -> arrayOf<Any>(userId, at ?: now, lowest, at ?: now) }
        if (rows.isEmpty()) return 0
        jdbc.batchUpdate(
            "insert into commerce_customers (user_id, status, joined_at, tier_code, tier_since, tier_basis_amount, migrated) " +
                "values (?, 'ACTIVE', ?, ?, ?, 0, true)",
            rows,
        )
        logger.info { "commerce customers backfilled: ${rows.size}" }
        return rows.size
    }

    private fun isMySql(): Boolean = dataSource.connection.use { it.metaData.databaseProductName }.contains("MySQL", ignoreCase = true)

    private fun fixMySqlSchema() {
        fun indexExists(name: String): Boolean =
            (
                jdbc.queryForObject(
                    "select count(*) from information_schema.statistics where table_schema = database() " +
                        "and table_name = 'user_coupons' and index_name = ?",
                    Long::class.java,
                    name,
                ) ?: 0L
            ) > 0
        if (!indexExists(NEW_UNIQUE)) {
            jdbc.execute("alter table user_coupons add constraint $NEW_UNIQUE unique (coupon_id, user_id, issue_key)")
            logger.info { "user_coupons: added unique $NEW_UNIQUE" }
        }
        if (indexExists(OLD_UNIQUE)) {
            jdbc.execute("alter table user_coupons drop index $OLD_UNIQUE")
            logger.info { "user_coupons: dropped old unique $OLD_UNIQUE" }
        }
        val type =
            jdbc
                .queryForList(
                    "select column_type from information_schema.columns where table_schema = database() " +
                        "and table_name = 'user_coupons' and column_name = 'source'",
                    String::class.java,
                ).firstOrNull()
        val missing = CouponSource.entries.filter { type != null && type.startsWith("enum(") && !type.contains("'${it.name}'") }
        if (missing.isNotEmpty()) {
            val values =
                CouponSource.entries
                    .map { it.name }
                    .sorted()
                    .joinToString(",") { "'$it'" }
            jdbc.execute("alter table user_coupons modify column source enum($values) not null")
            logger.info { "user_coupons.source enum extended with $missing" }
        }
    }

    companion object {
        const val OLD_UNIQUE = "uk_user_coupons"
        const val NEW_UNIQUE = "uk_user_coupons_issue"

        /** 회원이 처음 보인 시각을 찾는 곳들. push_consents 는 created_at 이 없어 바꾼 시각을 쓴다. */
        private val SOURCES =
            listOf(
                "select user_id, created_at as t from orders",
                "select user_id, created_at as t from wishlists",
                "select user_id, created_at as t from addresses",
                "select user_id, created_at as t from cart_items",
                "select user_id, created_at as t from reviews",
                "select user_id, coalesce(marketing_updated_at, night_updated_at) as t from push_consents",
                "select user_id, created_at as t from push_devices",
                "select user_id, created_at as t from user_coupons",
                "select user_id, created_at as t from attendance_checks",
            )

        fun defaultTiers(): List<Tier> =
            listOf(
                Tier("WELCOME", "웰컴", "#0EA5E9", 0, 1, 0),
                Tier("SILVER", "실버", "#64748B", 100_000, 2, 1),
                Tier("GOLD", "골드", "#D97706", 300_000, 3, 2),
                Tier("VIP", "VIP", "#7C3AED", 700_000, 5, 3),
            )
    }
}
