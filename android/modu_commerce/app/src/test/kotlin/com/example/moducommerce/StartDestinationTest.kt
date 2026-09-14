package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {

    @Test
    fun `토큰이 있으면 상품 목록으로 간다`() {
        assertEquals(StartDestination.PRODUCTS, StartDestination.of("eyJhbGciOi"))
    }

    @Test
    fun `토큰이 없으면 로그인 화면으로 간다`() {
        assertEquals(StartDestination.LOGIN, StartDestination.of(null))
    }

    @Test
    fun `토큰이 공백뿐이면 없는 것으로 본다`() {
        assertEquals(StartDestination.LOGIN, StartDestination.of("   "))
    }
}
