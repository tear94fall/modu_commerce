package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyStateTest {

    @Test
    fun `검색어가 없으면 상품이 없는 상태`() {
        assertEquals(EmptyState.NoProducts, EmptyState.of(null))
        assertEquals(EmptyState.NoProducts, EmptyState.of("  "))
    }

    @Test
    fun `검색어가 있으면 검색 결과가 없는 상태`() {
        assertEquals(EmptyState.NoResults("키보드"), EmptyState.of(" 키보드 "))
    }
}
