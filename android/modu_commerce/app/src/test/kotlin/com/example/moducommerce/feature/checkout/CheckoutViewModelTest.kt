package com.example.moducommerce.feature.checkout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.moducommerce.core.model.AddressInput
import com.example.moducommerce.core.model.OrderLine
import com.example.moducommerce.navigation.Routes
import com.example.moducommerce.testing.FakeCatalogRepository
import com.example.moducommerce.testing.FakeOrderRepository
import com.example.moducommerce.testing.MainDispatcherRule
import com.example.moducommerce.testing.address
import com.example.moducommerce.testing.cartItem
import com.example.moducommerce.testing.orderDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val orders = FakeOrderRepository()
    private val catalog = FakeCatalogRepository()

    private fun fromCart(vararg ids: Long) =
        CheckoutViewModel(SavedStateHandle(mapOf(Routes.ARG_CART_ITEM_IDS to ids.joinToString(","))), orders, catalog)

    @Test
    fun `장바구니에서 온 줄 중 주문 가능한 것만 싣고 기본 배송지를 고른다`() = runTest {
        orders.cartItems = mutableListOf(cartItem(1, quantity = 2), cartItem(2, available = false), cartItem(3))
        orders.addressList = mutableListOf(address(1), address(2, isDefault = true))
        val vm = fromCart(1, 2)

        val s = vm.uiState.value
        assertEquals(listOf(1L), s.lines.map { it.cartItemId })
        assertEquals(20_000L, s.totalAmount)
        assertEquals(2L, s.selectedAddressId)
        assertTrue(s.canPay)
    }

    @Test
    fun `결제하기는 주문을 만들고 장바구니 줄 id 를 넘기며 성공하면 주문 id 를 알린다`() = runTest {
        orders.cartItems = mutableListOf(cartItem(1, skuId = 10, quantity = 2))
        orders.addressList = mutableListOf(address(5, isDefault = true))
        orders.createOrderResult = Result.success(orderDetail(id = 77))
        val vm = fromCart(1)

        vm.ordered.test {
            vm.pay()
            assertEquals(77L, awaitItem())
        }
        val (addressId, lines, cartIds) = orders.lastCreate!!
        assertEquals(5L, addressId)
        assertEquals(listOf(OrderLine(10, 2)), lines)
        assertEquals(listOf(1L), cartIds)
        assertFalse(vm.uiState.value.paying)
    }

    @Test
    fun `재고 부족 400 은 서버 문구를 그대로 보여 준다`() = runTest {
        orders.cartItems = mutableListOf(cartItem(1))
        orders.addressList = mutableListOf(address(5, isDefault = true))
        orders.createOrderResult = Result.failure(FakeOrderRepository.badRequest("재고가 부족합니다: 상품 1 (남은 수량 0)"))
        val vm = fromCart(1)

        vm.pay()
        assertEquals("재고가 부족합니다: 상품 1 (남은 수량 0)", vm.uiState.value.messageText)
    }

    @Test
    fun `배송지가 없으면 결제할 수 없고 추가하면 그것이 선택된다`() = runTest {
        orders.cartItems = mutableListOf(cartItem(1))
        val vm = fromCart(1)
        assertFalse(vm.uiState.value.canPay)

        vm.openAddressForm()
        vm.saveAddress(AddressInput("임", "010-0000-0000", "06236", "서울", null, false))
        assertEquals(1L, vm.uiState.value.selectedAddressId)
        assertFalse(vm.uiState.value.showAddressForm)
        assertTrue(vm.uiState.value.canPay)
    }

    @Test
    fun `바로 구매는 상품 상세에서 한 줄을 만든다`() = runTest {
        val detail = com.example.moducommerce.core.model.ProductDetail(
            id = 20, name = "티셔츠", description = "", detail = null, images = listOf("https://img/1"), price = 19_000, listPrice = null, discountRate = 0,
            soldOut = false, wished = false, wishCount = 0, categoryPath = emptyList(), optionGroups = emptyList(),
            skus = listOf(com.example.moducommerce.core.model.Sku(30, emptyList(), "블랙 / L", 1_000, 5)),
        )
        catalog.detailResult = Result.success(detail)
        orders.addressList = mutableListOf(address(1, true))
        val vm = CheckoutViewModel(SavedStateHandle(mapOf<String, Any>(Routes.ARG_PRODUCT_ID to 20L, Routes.ARG_SKU_ID to 30L, Routes.ARG_QUANTITY to 3)), orders, catalog)

        val line = vm.uiState.value.lines.single()
        assertEquals(null, line.cartItemId)
        assertEquals(20_000L, line.unitPrice)
        assertEquals(60_000L, vm.uiState.value.totalAmount)
    }
}
