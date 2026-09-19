package com.example.moducommerce.feature.product

import com.example.moducommerce.core.model.OptionGroup
import com.example.moducommerce.core.model.OptionValue
import com.example.moducommerce.core.model.ProductDetail
import com.example.moducommerce.core.model.Sku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDetailUiStateTest {

    private val color = OptionGroup(1, "색상", listOf(OptionValue(11, "블랙"), OptionValue(12, "화이트")))
    private val size = OptionGroup(2, "사이즈", listOf(OptionValue(21, "M"), OptionValue(22, "L")))
    private val tshirt = ProductDetail(
        id = 7, name = "티셔츠", description = "", detail = null, images = emptyList(), price = 19_000, listPrice = null, discountRate = 0,
        soldOut = false, wished = false, wishCount = 0, categoryPath = emptyList(), optionGroups = listOf(color, size),
        skus = listOf(
            Sku(100, listOf(11, 21), "블랙 / M", 0, 5),
            Sku(101, listOf(11, 22), "블랙 / L", 1_000, 0),
            Sku(102, listOf(12, 21), "화이트 / M", 0, 2),
            Sku(103, listOf(12, 22), "화이트 / L", 1_000, 3),
        ),
    )

    @Test
    fun `모든 그룹을 골라야 SKU 가 정해지고 순서는 상관없다`() {
        assertNull(ProductDetailUiState.selectSku(tshirt, mapOf(1L to 11L)))
        assertEquals(103L, ProductDetailUiState.selectSku(tshirt, mapOf(2L to 22L, 1L to 12L))?.id)
    }

    @Test
    fun `옵션 없는 상품은 유일한 SKU`() {
        val plain = tshirt.copy(optionGroups = emptyList(), skus = listOf(Sku(1, emptyList(), "", 0, 9)))
        assertEquals(1L, ProductDetailUiState.selectSku(plain, emptyMap())?.id)
    }

    @Test
    fun `다른 그룹 선택과 조합해 재고가 없는 값은 고를 수 없다`() {
        // 블랙을 고른 뒤에는 L(블랙/L 재고 0)을 못 고른다
        assertFalse(ProductDetailUiState.isValueAvailable(tshirt, mapOf(1L to 11L), 2L, 22L))
        assertTrue(ProductDetailUiState.isValueAvailable(tshirt, mapOf(1L to 11L), 2L, 21L))
        // 아무것도 안 골랐으면 어느 SKU 든 재고가 있으면 된다
        assertTrue(ProductDetailUiState.isValueAvailable(tshirt, emptyMap(), 2L, 22L))
        // 같은 그룹의 다른 값은 "다른 그룹" 이 아니므로 무시된다(블랙을 골라도 화이트는 고를 수 있다)
        assertTrue(ProductDetailUiState.isValueAvailable(tshirt, mapOf(1L to 11L), 1L, 12L))
    }

    @Test
    fun `총액은 판매가와 추가금의 합에 수량을 곱한다`() {
        val state = ProductDetailUiState(detail = tshirt, selected = mapOf(1L to 12L, 2L to 22L), quantity = 2)
        assertEquals(40_000L, state.totalPrice)
        assertEquals(0L, ProductDetailUiState(detail = tshirt, selected = mapOf(1L to 12L)).totalPrice)
    }
}
