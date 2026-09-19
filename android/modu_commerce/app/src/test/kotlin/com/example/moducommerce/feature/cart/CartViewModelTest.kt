package com.example.moducommerce.feature.cart

import com.example.moducommerce.R
import com.example.moducommerce.testing.FakeOrderRepository
import com.example.moducommerce.testing.MainDispatcherRule
import com.example.moducommerce.testing.cartItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeOrderRepository()

    @Test
    fun `합계와 주문 가능 줄은 판매중이고 재고가 충분한 것만 센다`() = runTest {
        repository.cartItems = mutableListOf(cartItem(1, quantity = 2), cartItem(2, available = false), cartItem(3, quantity = 5, stock = 2))
        val vm = CartViewModel(repository)
        vm.load()

        assertEquals(listOf(1L), vm.uiState.value.orderable.map { it.id })
        assertEquals(20_000L, vm.uiState.value.totalAmount)
        assertEquals(2, vm.uiState.value.totalQuantity)
    }

    @Test
    fun `수량은 먼저 바꾸고 실패하면 안내 뒤 다시 받아 온다`() = runTest {
        repository.cartItems = mutableListOf(cartItem(1, quantity = 2))
        val vm = CartViewModel(repository)
        vm.load()

        vm.changeQuantity(1, +1)
        assertEquals(3, vm.uiState.value.items.single().quantity)
        assertEquals(30_000L, vm.uiState.value.items.single().lineAmount)
        assertEquals(listOf(1L to 3), repository.quantityCalls)
        vm.changeQuantity(1, -5) // 1 아래로는 내려가지 않는다
        assertEquals(1, vm.uiState.value.items.single().quantity)

        repository.quantityResult = Result.failure(RuntimeException("500"))
        vm.changeQuantity(1, +1)
        assertEquals(R.string.cart_update_failed, vm.uiState.value.messageRes)
        assertEquals("서버 값(2)으로 되돌아온다", 2, vm.uiState.value.items.single().quantity)
        vm.consumeMessage()
        assertNull(vm.uiState.value.messageRes)
    }

    @Test
    fun `삭제는 바로 목록에서 빠진다`() = runTest {
        repository.cartItems = mutableListOf(cartItem(1), cartItem(2))
        val vm = CartViewModel(repository)
        vm.load()
        vm.remove(1)
        assertEquals(listOf(2L), vm.uiState.value.items.map { it.id })
        assertEquals(listOf(1L), repository.removeCalls)
    }
}
