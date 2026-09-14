package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductTest {

    @Test
    fun `상품 목록 JSON 을 파싱한다`() {
        val list = Product.parseList(
            """[{"id":1,"name":"모두 텀블러","description":"차가운","price":24000,"imageUrl":null},
                {"id":2,"name":"모두 백팩","description":"방수","price":59000}]"""
        )
        assertEquals(2, list.size)
        assertEquals("모두 텀블러", list[0].name)
        assertEquals(24000L, list[0].price)
        assertEquals(null, list[1].imageUrl)
    }

    @Test
    fun `가격은 천 단위 콤마와 원으로 표시한다`() {
        assertEquals("129,000원", Product(name = "키보드", price = 129_000).priceLabel())
    }

    @Test
    fun `이미지 주소가 있으면 그대로 쓴다`() {
        assertEquals("https://img/keyboard.png", Product(imageUrl = "https://img/keyboard.png").imageUrlOrNull())
    }

    @Test
    fun `이미지 주소가 비어 있으면 없는 것으로 본다`() {
        assertEquals(null, Product(imageUrl = "  ").imageUrlOrNull())
        assertEquals(null, Product(imageUrl = null).imageUrlOrNull())
    }

    @Test
    fun `단건 JSON 을 파싱한다`() {
        val p = Product.parse("""{"id":7,"name":"모두 우산","description":"자동","price":15000,"imageUrl":"https://img/u.png"}""")
        assertEquals(7L, p.id)
        assertEquals("모두 우산", p.name)
        assertEquals("https://img/u.png", p.imageUrlOrNull())
    }
}
