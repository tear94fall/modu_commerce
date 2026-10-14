package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.SkuSpec
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.domain.repository.rw.WishlistRwRepository
import com.example.commerce.application.usecase.command.ProductCommand
import com.example.commerce.application.usecase.product.CreateProductUseCase
import com.example.commerce.application.usecase.product.UpdateProductUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** 관리자 상품 저장이 그사이 바뀐 재고·찜 수·리뷰 집계를 덮어쓰지 않는다. */
@SpringBootTest
class ProductConsistencyTest
    @Autowired
    constructor(
        private val createProductUseCase: CreateProductUseCase,
        private val updateProductUseCase: UpdateProductUseCase,
        private val wishlistCommandService: WishlistCommandService,
        private val productRwRepository: ProductRwRepository,
        private val wishlistRwRepository: WishlistRwRepository,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        private val jdbc = JdbcTemplate(dataSource)

        private fun command(sku: SkuSpec) =
            ProductCommand(
                name = "모두 우산",
                description = "자동 우산",
                detail = null,
                price = 15_000,
                listPrice = null,
                categoryId = null,
                status = ProductStatus.SELLING,
                images = emptyList(),
                optionGroups = emptyList(),
                skus = listOf(sku),
            )

        private fun stock(productId: Long): Int =
            jdbc.queryForObject("select stock from product_skus where product_id = ?", Int::class.java, productId)!!

        private fun counter(
            productId: Long,
            column: String,
        ): Long = jdbc.queryForObject("select $column from products where id = ?", Long::class.java, productId)!!

        @BeforeEach
        fun cleanUp() {
            wishlistRwRepository.deleteAll()
            productRwRepository.deleteAll()
        }

        @Test
        fun `기준 재고를 주면 화면을 연 뒤 팔린 수량을 지우지 않고 증감만 더한다`() {
            val id = createProductUseCase.execute(command(SkuSpec(emptyMap(), 0, 10))).id
            // 관리자가 재고 10 을 보고 있는 동안 주문 3개가 팔렸다.
            jdbc.update("update product_skus set stock = 7 where product_id = ?", id)

            updateProductUseCase.execute(id, command(SkuSpec(emptyMap(), 0, stock = 15, baseStock = 10)))

            assertEquals(12, stock(id)) // 7 + (15 - 10)
        }

        @Test
        fun `기준 재고 증감은 0 아래로 내려가지 않고 기준 재고가 없으면 예전처럼 덮어쓴다`() {
            val id = createProductUseCase.execute(command(SkuSpec(emptyMap(), 0, 10))).id
            jdbc.update("update product_skus set stock = 2 where product_id = ?", id)

            updateProductUseCase.execute(id, command(SkuSpec(emptyMap(), 0, stock = 0, baseStock = 10)))
            assertEquals(0, stock(id))

            updateProductUseCase.execute(id, command(SkuSpec(emptyMap(), 0, stock = 9)))
            assertEquals(9, stock(id))
        }

        @Test
        fun `동시에 찜해도 찜 수를 잃지 않고 상품 저장이 찜 수와 리뷰 집계를 덮어쓰지 않는다`() {
            val id = createProductUseCase.execute(command(SkuSpec(emptyMap(), 0, 10))).id
            val users = (1..8).map { "wish-$it" }
            val pool = Executors.newFixedThreadPool(users.size)
            val start = CountDownLatch(1)
            val futures = users.map { u -> pool.submit { start.await().also { wishlistCommandService.add(u, id) } } }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
            pool.shutdown()
            assertEquals(8L, counter(id, "wish_count"))

            // 리뷰 집계는 리뷰 서비스가 원자적으로 바꾼다. 여기서는 DB 값이 상품 저장에 덮이지 않는지만 본다.
            jdbc.update("update products set review_count = 3, rating_sum = 12 where id = ?", id)
            updateProductUseCase.execute(id, command(SkuSpec(emptyMap(), 0, 10)))
            assertEquals(8L, counter(id, "wish_count"))
            assertEquals(3L, counter(id, "review_count"))
            assertEquals(12L, counter(id, "rating_sum"))

            wishlistCommandService.remove("wish-1", id)
            assertEquals(7L, counter(id, "wish_count"))
        }
    }
