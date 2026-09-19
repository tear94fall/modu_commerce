package com.example.moducommerce.feature.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.model.Address
import com.example.moducommerce.core.model.AddressInput
import com.example.moducommerce.core.model.CartItem
import com.example.moducommerce.core.model.OrderLine
import com.example.moducommerce.core.network.ApiException
import com.example.moducommerce.data.repository.CatalogRepository
import com.example.moducommerce.data.repository.OrderRepository
import com.example.moducommerce.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 주문서 한 줄. 장바구니에서 왔으면 [cartItemId] 가 있고, 바로 구매면 null. */
data class CheckoutLine(
    val cartItemId: Long?,
    val skuId: Long,
    val productName: String,
    val optionLabel: String,
    val imageUrl: String?,
    val unitPrice: Long,
    val quantity: Int,
) {
    val lineAmount: Long get() = unitPrice * quantity
}

data class CheckoutUiState(
    val lines: List<CheckoutLine> = emptyList(),
    val addresses: List<Address> = emptyList(),
    val selectedAddressId: Long? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val paying: Boolean = false,
    val showAddressPicker: Boolean = false,
    val showAddressForm: Boolean = false,
    val messageText: String? = null,
) {
    val selectedAddress: Address? get() = addresses.firstOrNull { it.id == selectedAddressId }
    val totalAmount: Long get() = lines.sumOf { it.lineAmount }
    val canPay: Boolean get() = !paying && lines.isNotEmpty() && selectedAddress != null
}

/**
 * 주문서. 진입 방식 두 가지:
 * - 장바구니: `cartItemIds` 인자로 장바구니 줄 id 들이 온다 → 장바구니를 읽어 그 줄만 남긴다.
 * - 바로 구매: `skuId`, `quantity` 인자가 온다 → 상품 상세를 읽어 한 줄을 만든다.
 * 결제는 모의라 "결제하기" 가 곧 주문 생성이다.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val cartItemIds: List<Long> = savedStateHandle.get<String>(Routes.ARG_CART_ITEM_IDS)
        ?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
    private val directSkuId: Long = savedStateHandle.get<Long>(Routes.ARG_SKU_ID) ?: -1L
    private val directProductId: Long = savedStateHandle.get<Long>(Routes.ARG_PRODUCT_ID) ?: -1L
    private val directQuantity: Int = savedStateHandle.get<Int>(Routes.ARG_QUANTITY) ?: 1

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val _ordered = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 주문이 만들어졌다(주문 id). 화면이 주문 상세로 간다. */
    val ordered: SharedFlow<Long> = _ordered.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val lines = loadLines()
            val addresses = orderRepository.addresses().getOrNull()
            if (lines == null || addresses == null) {
                _uiState.update { it.copy(loading = false, error = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    lines = lines,
                    addresses = addresses,
                    selectedAddressId = it.selectedAddressId ?: (addresses.firstOrNull { a -> a.isDefault } ?: addresses.firstOrNull())?.id,
                    loading = false,
                )
            }
        }
    }

    private suspend fun loadLines(): List<CheckoutLine>? {
        if (cartItemIds.isNotEmpty()) {
            val cart = orderRepository.cart().getOrNull() ?: return null
            return cart.items.filter { it.id in cartItemIds && it.orderable }.map { it.toLine() }
        }
        if (directSkuId > 0 && directProductId > 0) {
            val detail = catalogRepository.product(directProductId).getOrNull() ?: return null
            val sku = detail.skus.firstOrNull { it.id == directSkuId } ?: return null
            return listOf(
                CheckoutLine(
                    cartItemId = null,
                    skuId = sku.id,
                    productName = detail.name,
                    optionLabel = sku.optionLabel,
                    imageUrl = detail.images.firstOrNull(),
                    unitPrice = detail.price + sku.extraPrice,
                    quantity = directQuantity.coerceAtLeast(1),
                ),
            )
        }
        return emptyList()
    }

    fun selectAddress(id: Long) = _uiState.update { it.copy(selectedAddressId = id, showAddressPicker = false) }

    fun openPicker() = _uiState.update { it.copy(showAddressPicker = true) }

    fun closePicker() = _uiState.update { it.copy(showAddressPicker = false) }

    fun openAddressForm() = _uiState.update { it.copy(showAddressForm = true, showAddressPicker = false) }

    fun closeAddressForm() = _uiState.update { it.copy(showAddressForm = false) }

    fun saveAddress(input: AddressInput) {
        viewModelScope.launch {
            orderRepository.createAddress(input)
                .onSuccess { created ->
                    val addresses = orderRepository.addresses().getOrDefault(_uiState.value.addresses + created)
                    _uiState.update { it.copy(addresses = addresses, selectedAddressId = created.id, showAddressForm = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(messageText = e.userMessage(fallbackRes = R.string.address_save_failed)) } }
        }
    }

    fun pay() {
        val state = _uiState.value
        val address = state.selectedAddress ?: return
        if (!state.canPay) return
        _uiState.update { it.copy(paying = true) }
        viewModelScope.launch {
            orderRepository.createOrder(
                addressId = address.id,
                items = state.lines.map { OrderLine(it.skuId, it.quantity) },
                cartItemIds = state.lines.mapNotNull { it.cartItemId },
            ).onSuccess { order ->
                _uiState.update { it.copy(paying = false) }
                _ordered.tryEmit(order.id)
            }.onFailure { e ->
                _uiState.update { it.copy(paying = false, messageText = e.userMessage(fallbackRes = R.string.checkout_failed)) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(messageText = null) }

    /** 스낵바 문구를 만든다. 400 이면 서버 문구(재고 부족 등)를 그대로 쓴다. 문자열 리소스는 화면이 푼다. */
    private fun Throwable.userMessage(fallbackRes: Int): String =
        (this as? ApiException)?.takeIf { it.code == 400 }?.serverMessage() ?: "res:$fallbackRes"

    private fun CartItem.toLine() = CheckoutLine(id, skuId, productName, optionLabel, imageUrl, unitPrice, quantity)

    companion object {
        /** `{"message":"..."}` 본문에서 문구만. */
        fun ApiException.serverMessage(): String? =
            body?.let { Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(it)?.groupValues?.get(1) }
    }
}
