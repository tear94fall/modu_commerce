package com.example.moducommerce.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.model.ProductDetail
import com.example.moducommerce.core.model.Sku
import com.example.moducommerce.core.network.isNotFound
import com.example.moducommerce.core.session.WishStore
import com.example.moducommerce.data.repository.CatalogRepository
import com.example.moducommerce.data.repository.OrderRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.moducommerce.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val detail: ProductDetail? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: Boolean = false,
    /** 옵션 그룹 id → 고른 값 id. */
    val selected: Map<Long, Long> = emptyMap(),
    val quantity: Int = 1,
    val sheetOpen: Boolean = false,
    val messageRes: Int? = null,
    /** 장바구니 담는 중. 버튼을 잠근다. */
    val working: Boolean = false,
) {
    /** 모든 그룹을 골랐을 때의 SKU. 옵션 없는 상품은 유일한 SKU. 아직 덜 골랐으면 null. */
    val selectedSku: Sku? get() = detail?.let { selectSku(it, selected) }

    val totalPrice: Long get() = detail?.let { d -> selectedSku?.let { (d.price + it.extraPrice) * quantity } } ?: 0L

    companion object {
        fun selectSku(detail: ProductDetail, selected: Map<Long, Long>): Sku? {
            if (detail.optionGroups.isEmpty()) return detail.skus.firstOrNull()
            if (selected.size < detail.optionGroups.size) return null
            val chosen = selected.values.toSet()
            return detail.skus.firstOrNull { it.optionValueIds.toSet() == chosen }
        }

        /**
         * 이 값을 고를 수 있는가: 이미 고른 다른 그룹의 값들과 함께 재고가 있는 SKU 가 하나라도 있으면 된다.
         * 그룹 하나뿐이면 그 값을 가진 SKU 의 재고만 본다.
         */
        fun isValueAvailable(detail: ProductDetail, selected: Map<Long, Long>, groupId: Long, valueId: Long): Boolean {
            val others = selected.filterKeys { it != groupId }.values.toSet()
            return detail.skus.any { sku -> !sku.soldOut && valueId in sku.optionValueIds && others.all { it in sku.optionValueIds } }
        }
    }
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val wishStore: WishStore,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val productId: Long = savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0L

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    /** 바로 구매: (skuId, quantity). 화면이 주문서로 간다. */
    data class BuyNow(val productId: Long, val skuId: Long, val quantity: Int)

    private val _buyNow = MutableSharedFlow<BuyNow>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val buyNow: SharedFlow<BuyNow> = _buyNow.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            wishStore.changes.collect { change ->
                if (change.productId != productId) return@collect
                _uiState.update { s -> s.copy(detail = s.detail?.copy(wished = change.wished)) }
            }
        }
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = false, notFound = false) }
        viewModelScope.launch {
            catalogRepository.product(productId)
                .onSuccess { detail -> _uiState.update { it.copy(detail = detail, loading = false, selected = emptyMap(), quantity = 1) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, notFound = e.isNotFound(), error = !e.isNotFound()) } }
        }
    }

    fun toggleWish() {
        val detail = _uiState.value.detail ?: return
        val next = !detail.wished
        _uiState.update { it.copy(detail = detail.copy(wished = next, wishCount = (detail.wishCount + if (next) 1 else -1).coerceAtLeast(0))) }
        viewModelScope.launch {
            wishStore.set(productId, next).onFailure {
                _uiState.update { s -> s.copy(detail = s.detail?.copy(wished = !next, wishCount = detail.wishCount), messageRes = R.string.detail_wish_failed) }
            }
        }
    }

    fun openSheet() = _uiState.update { it.copy(sheetOpen = true) }

    fun closeSheet() = _uiState.update { it.copy(sheetOpen = false) }

    /** 같은 값을 다시 누르면 해제. 값을 바꾸면 수량은 1로 돌아간다(재고가 달라지므로). */
    fun selectValue(groupId: Long, valueId: Long) {
        _uiState.update { s ->
            val next = if (s.selected[groupId] == valueId) s.selected - groupId else s.selected + (groupId to valueId)
            s.copy(selected = next, quantity = 1)
        }
    }

    fun changeQuantity(delta: Int) {
        _uiState.update { s ->
            val max = s.selectedSku?.stock?.coerceAtLeast(1) ?: 1
            s.copy(quantity = (s.quantity + delta).coerceIn(1, max))
        }
    }

    fun addToCart() {
        val state = _uiState.value
        val sku = state.selectedSku ?: return
        if (state.working) return
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            orderRepository.addToCart(sku.id, state.quantity)
                .onSuccess { _uiState.update { it.copy(working = false, sheetOpen = false, messageRes = R.string.option_added) } }
                .onFailure { _uiState.update { it.copy(working = false, messageRes = R.string.option_add_failed) } }
        }
    }

    fun buyNow() {
        val state = _uiState.value
        val sku = state.selectedSku ?: return
        _uiState.update { it.copy(sheetOpen = false) }
        _buyNow.tryEmit(BuyNow(productId, sku.id, state.quantity))
    }

    fun consumeMessage() = _uiState.update { it.copy(messageRes = null) }
}
