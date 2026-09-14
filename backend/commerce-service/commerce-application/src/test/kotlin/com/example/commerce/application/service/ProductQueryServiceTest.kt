package com.example.commerce.application.service

import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.seed.ProductSeeder
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductQueryServiceTest
    @Autowired
    constructor(
        private val productQueryService: ProductQueryService,
        private val productRwRepository: ProductRwRepository,
    ) {
        @BeforeEach
        fun seed() {
            productRwRepository.deleteAll()
            productRwRepository.saveAll(ProductSeeder.sampleProducts())
        }

        @Test
        fun `시드 상품 4개를 id 순으로 조회한다`() {
            val products = productQueryService.findProducts(null)

            assertEquals(ProductSeeder.SAMPLE_COUNT, products.size)
            assertEquals(listOf("모두 무선 이어폰", "모두 텀블러 500ml", "모두 데일리 백팩", "모두 기계식 키보드"), products.map { it.name })
            assertEquals(89_000L, products.first().price)
        }

        @Test
        fun `키워드로 이름과 설명을 검색한다`() {
            assertEquals(listOf("모두 기계식 키보드"), productQueryService.findProducts("키보드").map { it.name })
            assertEquals(listOf("모두 데일리 백팩"), productQueryService.findProducts("노트북").map { it.name })
            assertEquals(emptyList<String>(), productQueryService.findProducts("없는상품").map { it.name })
        }

        @Test
        fun `없는 상품은 EntityNotFoundException`() {
            assertThrows(EntityNotFoundException::class.java) { productQueryService.findProduct(999_999L) }
        }

        @Test
        fun `admin 페이지 검색은 페이지 번호와 크기를 안전한 범위로 자른다`() {
            val clamped = productQueryService.searchAdminProducts(null, -3, 1000)
            assertEquals(0, clamped.number)
            assertEquals(100, clamped.size)
            assertEquals("모두 기계식 키보드", clamped.content.first().name)

            assertEquals(1, productQueryService.searchAdminProducts(null, 0, 0).size)
        }
    }
