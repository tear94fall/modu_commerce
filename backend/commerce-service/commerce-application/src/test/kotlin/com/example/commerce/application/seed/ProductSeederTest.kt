package com.example.commerce.application.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductSeederTest {
    @Test
    fun `테스트 상품은 선언한 개수만큼 만든다`() {
        assertEquals(ProductSeeder.SAMPLE_COUNT, ProductSeeder.sampleProducts().size)
    }

    @Test
    fun `카테고리는 상위 4개 하위 10개이고 하위는 상위를 가리킨다`() {
        val categories = ProductSeeder.sampleCategories()
        assertEquals(ProductSeeder.ROOT_CATEGORY_COUNT, categories.count { it.isRoot() })
        assertEquals(ProductSeeder.CHILD_CATEGORY_COUNT, categories.count { !it.isRoot() })
        assertTrue(categories.filter { !it.isRoot() }.all { it.parent in categories })
    }

    @Test
    fun `상품마다 사진이 두 장 이상 있고 대표 사진은 상품마다 다르다`() {
        val products = ProductSeeder.sampleProducts()
        products.forEach { assertTrue(it.images.size >= 2, "${it.name} 에 사진이 부족하다") }
        val urls = products.map { it.imageUrl }
        assertEquals(urls.size, urls.toSet().size)
    }

    @Test
    fun `모든 상품이 카테고리에 배정되고 옵션 상품과 품절 상품이 있다`() {
        val categories = ProductSeeder.sampleCategories().associateBy { it.name }
        val products = ProductSeeder.sampleProducts(categories)
        assertTrue(products.all { it.category != null && !it.category!!.isRoot() })
        val tshirt = products.first { it.name == "모두 베이직 티셔츠" }
        assertEquals(6, tshirt.skus.size)
        assertEquals(2, tshirt.optionGroups.size)
        assertTrue(products.any { it.isSoldOut() })
        assertTrue(products.any { it.discountRate() > 0 })
        assertTrue(products.all { it.skus.isNotEmpty() })
    }

    @Test
    fun `기존 상품 이름 4개는 그대로 있다`() {
        val names = ProductSeeder.sampleProducts().map { it.name }
        listOf("모두 무선 이어폰", "모두 텀블러 500ml", "모두 데일리 백팩", "모두 기계식 키보드").forEach { assertTrue(it in names, it) }
    }
}
