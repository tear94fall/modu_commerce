package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommerceApiClientTest {

    @Test
    fun `검색어가 없거나 공백이면 q 를 붙이지 않는다`() {
        assertEquals("http://h:8200/api/v1/products", CommerceApiClient.productsUrl("http://h:8200/", null).toString())
        assertEquals("http://h:8200/api/v1/products", CommerceApiClient.productsUrl("http://h:8200", "   ").toString())
    }

    @Test
    fun `한글 검색어는 앞뒤 공백을 떼고 UTF-8 로 인코딩한다`() {
        val url = CommerceApiClient.productsUrl("http://h:8200/", " 키보드 ")

        assertEquals("키보드", url.queryParameter("q"))
        assertTrue(url.toString().endsWith("?q=%ED%82%A4%EB%B3%B4%EB%93%9C"))
    }
}
