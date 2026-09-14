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
    fun `테스트 상품마다 이미지 주소가 있다`() {
        // 앱의 상품 목록이 플레이스홀더가 아닌 실제 사진을 보여 주려면 시드에 주소가 있어야 한다.
        ProductSeeder.sampleProducts().forEach {
            assertTrue(!it.imageUrl.isNullOrBlank(), "${it.name} 에 이미지 주소가 없다")
        }
    }

    @Test
    fun `이미지 주소는 상품마다 다르다`() {
        val urls = ProductSeeder.sampleProducts().map { it.imageUrl }
        assertEquals(urls.size, urls.toSet().size)
    }
}
