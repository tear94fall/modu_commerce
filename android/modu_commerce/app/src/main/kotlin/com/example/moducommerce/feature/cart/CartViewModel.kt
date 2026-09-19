package com.example.moducommerce.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.model.CartItem
import com.example.moducommerce.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartUiState(
    val items: List<CartItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val messageRes: Int? = null,
) {
    val orderable: List<CartItem> get() = items.filter { it.orderable }
    val totalAmount: Long get() = orderable.sumOf { it.lineAmount }
    val totalQuantity: Int get() = orderable.sumOf { it.quantity }
}

/** 장바구니. 수량 변경은 먼저 화면을 바꾸고 실패하면 다시 받아 온다. 주문은 주문 가능한 줄만 넘긴다. */
@HiltViewModel
class CartViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(loading = it.items.isEmpty(), error = false) }
        viewModelScope.launch {
            orderRepository.cart()
                .onSuccess { cart -> _uiState.update { it.copy(items = cart.items, loading = false) } }
                .onFailure { _uiState.update { it.copy(loading = false, error = true) } }
        }
    }

    fun changeQuantity(itemId: Long, delta: Int) {
        val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return
        val next = (item.quantity + delta).coerceIn(1, MAX_QUANTITY)
        if (next == item.quantity) return
        _uiState.update { s -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(quantity = next, lineAmount = it.unitPrice * next) else it }) }
        viewModelScope.launch {
            orderRepository.changeQuantity(itemId, next).onFailure {
                _uiState.update { it.copy(messageRes = R.string.cart_update_failed) }
                load()
            }
        }
    }

    fun remove(itemId: Long) {
        _uiState.update { s -> s.copy(items = s.items.filterNot { it.id == itemId }) }
        viewModelScope.launch {
            orderRepository.removeCartItem(itemId).onFailure {
                _uiState.update { it.copy(messageRes = R.string.cart_remove_failed) }
                load()
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(messageRes = null) }

    companion object {
        const val MAX_QUANTITY = 99
    }
}
