package com.example.commerce.api.support

import com.example.commerce.application.domain.repository.rw.CategoryRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.seed.CustomerMigration
import com.example.commerce.application.seed.ProductSeeder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.CacheManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * 컨트롤러 테스트들이 같은 H2 컨텍스트를 나눠 쓰므로, 각 테스트 앞뒤로 시드 상태(카테고리 14, 상품 24)로 되돌린다.
 * 소프트 삭제된 행은 JPA deleteAll 이 못 보므로 자식 테이블부터 네이티브로 비운다.
 */
@Component
class CatalogTestSupport(
    private val productRwRepository: ProductRwRepository,
    private val categoryRwRepository: CategoryRwRepository,
    @Qualifier("rwDataSource") rwDataSource: DataSource,
    private val cacheManager: CacheManager,
    private val customerMigration: CustomerMigration,
) {
    private val jdbc = JdbcTemplate(rwDataSource)

    fun reseed() {
        // 테스트끼리 같은 컨텍스트(같은 캐시)를 쓴다. 지운 행이 캐시에 남지 않게 먼저 비운다.
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
        listOf(
            "commerce_tier_histories",
            "commerce_tier_runs",
            "commerce_customers",
            "commerce_tier_coupons",
            "commerce_tiers",
            "push_inbox_items",
            "push_campaign_opens",
            "push_campaigns",
            "push_devices",
            "push_consents",
            "promotion_coupons",
            "attendance_checks",
            "promotion_products",
            "promotions",
            "user_coupons",
            "coupon_scope_targets",
            "coupons",
            "reviews",
            "point_outbox",
            "order_items",
            "orders",
            "cart_items",
            "addresses",
            "sku_option_values",
            "product_skus",
            "product_option_values",
            "product_option_groups",
            "product_images",
            "wishlists",
            "products",
        ).forEach { jdbc.update("delete from $it") }
        // 3단계라 하위부터 지우는 순서가 안 맞을 수 있다 — 부모 링크를 먼저 끊는다(테스트 H2 에는 FK 가 있다).
        jdbc.update("update categories set parent_id = null")
        jdbc.update("delete from categories")
        val categories = categoryRwRepository.saveAll(ProductSeeder.sampleCategories()).associateBy { it.name }
        productRwRepository.saveAll(ProductSeeder.sampleProducts(categories))
        customerMigration.seedTiers()
        // 기존 테스트의 사용자들은 커머스에 가입(약관 동의)한 고객으로 둔다. 가입 전 동작은 따로 "99" 같은 사용자로 본다.
        TEST_CUSTOMERS.forEach { joinCustomer(it) }
    }

    /** 가입·동의한 고객 행을 바로 넣는다(가입 API 를 거치지 않는다). */
    fun joinCustomer(
        userId: String,
        tierCode: String = "WELCOME",
        agreed: Boolean = true,
        migrated: Boolean = false,
    ) {
        val now = Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 0, 0))
        jdbc.update(
            "insert into commerce_customers (user_id, status, joined_at, terms_agreed_at, privacy_agreed_at, terms_version, " +
                "tier_code, tier_since, tier_basis_amount, migrated) values (?, 'ACTIVE', ?, ?, ?, ?, ?, ?, 0, ?)",
            userId,
            now,
            if (agreed) now else null,
            if (agreed) now else null,
            if (agreed) "2026-10" else null,
            tierCode,
            now,
            migrated,
        )
    }

    companion object {
        val TEST_CUSTOMERS = listOf("11", "22", "u1", "u2", "u3", "u4")
    }

    fun productId(name: String): Long = requireNotNull(productRwRepository.findAll().first { it.name == name }.id)

    fun categoryId(name: String): Long = requireNotNull(categoryRwRepository.findAll().first { it.name == name }.id)

    /** 상품을 다른 카테고리로 바로 옮긴다(상품 수정 API 를 거치지 않는다). */
    fun moveProduct(
        productName: String,
        categoryId: Long,
    ) {
        jdbc.update("update products set category_id = ? where name = ?", categoryId, productName)
    }

    /** 상품의 SKU id(옵션 라벨로 고른다. 옵션 없는 상품은 라벨 ""). */
    @Transactional(transactionManager = "rwTransactionManager")
    fun skuId(
        productName: String,
        optionLabel: String = "",
    ): Long {
        val product = productRwRepository.findAll().first { it.name == productName }
        return requireNotNull(product.skus.first { it.optionLabel() == optionLabel }.id)
    }

    @Transactional(transactionManager = "rwTransactionManager")
    fun stockOf(
        productName: String,
        optionLabel: String = "",
    ): Int =
        productRwRepository
            .findAll()
            .first { it.name == productName }
            .skus
            .first { it.optionLabel() == optionLabel }
            .stock
}
