package com.example.commerce.application.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReviewTest {
    @Test
    fun `이름은 가운데를 가린다`() {
        assertEquals("임*섭", Review.maskName("임준섭"))
        assertEquals("김*", Review.maskName("김철"))
        assertEquals("*", Review.maskName("김"))
        assertEquals("홍**님", Review.maskName("홍길동님"))
        assertEquals("모두 회원", Review.maskName(null))
        assertEquals("모두 회원", Review.maskName("  "))
    }

    @Test
    fun `별점과 길이를 검사한다`() {
        assertThrows(IllegalArgumentException::class.java) { Review.validRating(0) }
        assertThrows(IllegalArgumentException::class.java) { Review.validRating(6) }
        assertEquals(3, Review.validRating(3))
        assertThrows(IllegalArgumentException::class.java) { Review.validContent("짧다") }
        assertThrows(IllegalArgumentException::class.java) { Review.validContent("a".repeat(1001)) }
        assertEquals("열 글자는 넘는 리뷰 내용", Review.validContent("  열 글자는 넘는 리뷰 내용  "))
    }

    @Test
    fun `상품 평점 캐시는 추가·교체·제거를 따라간다`() {
        val product = Product(name = "머그컵", description = "d", price = 1000)
        assertEquals(0.0, product.ratingAverage())
        product.addRating(5)
        product.addRating(4)
        assertEquals(2, product.reviewCount)
        assertEquals(4.5, product.ratingAverage())
        product.replaceRating(4, 1)
        assertEquals(3.0, product.ratingAverage())
        product.removeRating(5)
        assertEquals(1.0, product.ratingAverage())
        product.removeRating(1)
        assertEquals(0, product.reviewCount)
        assertEquals(0.0, product.ratingAverage())
    }
}
