package com.example.commerce.application.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ProductTest {
    @Test
    fun `저장 전 서로 다른 Product는 동등하지 않다`() {
        val a = Product.create(name = "a", description = "a", price = 1)
        val b = Product.create(name = "b", description = "b", price = 2)

        assertNotEquals(a, b)
        assertFalse(a.isPersisted())
    }

    @Test
    fun `create는 전달받은 값으로 Product를 만든다`() {
        val product = Product.create(name = "텀블러", description = "차가운", price = 24_000)

        assertEquals("텀블러", product.name)
        assertEquals(24_000L, product.price)
        assertEquals(null, product.imageUrl)
    }
}
