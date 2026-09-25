package com.example.commerce.api.support

import com.example.commerce.application.domain.repository.rw.CategoryRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.seed.ProductSeeder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
) {
    private val jdbc = JdbcTemplate(rwDataSource)

    fun reseed() {
        listOf(
            "attendance_checks",
            "promotion_products",
            "promotions",
            "reviews",
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
        jdbc.update("delete from categories where parent_id is not null")
        jdbc.update("delete from categories")
        val categories = categoryRwRepository.saveAll(ProductSeeder.sampleCategories()).associateBy { it.name }
        productRwRepository.saveAll(ProductSeeder.sampleProducts(categories))
    }

    fun productId(name: String): Long = requireNotNull(productRwRepository.findAll().first { it.name == name }.id)

    fun categoryId(name: String): Long = requireNotNull(categoryRwRepository.findAll().first { it.name == name }.id)

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
