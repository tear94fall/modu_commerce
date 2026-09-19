package com.example.moducommerce.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.model.OrderDetail
import com.example.moducommerce.core.model.OrderSummary
import com.example.moducommerce.core.network.isNotFound
import com.example.moducommerce.data.repository.OrderRepository
import com.example.moducommerce.feature.checkout.CheckoutViewModel.Companion.serverMessage
import com.example.moducommerce.core.network.ApiException
import com.example.moducommerce.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(
    val orders: List<OrderSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val hasNext: Boolean = false,
    val page: Int = 0,
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            orderRepository.orders(page = 0)
                .onSuccess { page -> _uiState.update { it.copy(orders = page.content, loading = false, hasNext = page.hasNext, page = 0) } }
                .onFailure { _uiState.update { it.copy(loading = false, error = true) } }
        }
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.loading || s.loadingMore || !s.hasNext) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            orderRepository.orders(page = s.page + 1)
                .onSuccess { page -> _uiState.update { it.copy(orders = it.orders + page.content, loadingMore = false, hasNext = page.hasNext, page = page.number) } }
                .onFailure { _uiState.update { it.copy(loadingMore = false) } }
        }
    }
}

data class OrderDetailUiState(
    val order: OrderDetail? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: Boolean = false,
    val working: Boolean = false,
    val messageText: String? = null,
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val orderId: Long = savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0L

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = false, notFound = false) }
        viewModelScope.launch {
            orderRepository.order(orderId)
                .onSuccess { order -> _uiState.update { it.copy(order = order, loading = false) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, notFound = e.isNotFound(), error = !e.isNotFound()) } }
        }
    }

    fun cancel() {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            orderRepository.cancelOrder(orderId)
                .onSuccess { order -> _uiState.update { it.copy(order = order, working = false, messageText = "res:${R.string.order_cancelled_done}") } }
                .onFailure { e ->
                    val text = (e as? ApiException)?.takeIf { it.code == 400 }?.serverMessage() ?: "res:${R.string.order_cancel_failed}"
                    _uiState.update { it.copy(working = false, messageText = text) }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(messageText = null) }
}
