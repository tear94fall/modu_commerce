package com.example.moducommerce.data.repository

import com.example.moducommerce.core.model.Address
import com.example.moducommerce.core.model.AddressInput
import com.example.moducommerce.core.model.Cart
import com.example.moducommerce.core.model.CartItem
import com.example.moducommerce.core.model.OrderDetail
import com.example.moducommerce.core.model.OrderLine
import com.example.moducommerce.core.model.OrderSummary
import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.network.safeCall
import com.example.moducommerce.data.api.OrderApi
import com.example.moducommerce.data.dto.AddCartItemRequestDto
import com.example.moducommerce.data.dto.AddressRequestDto
import com.example.moducommerce.data.dto.ChangeQuantityRequestDto
import com.example.moducommerce.data.dto.CreateOrderRequestDto
import com.example.moducommerce.data.dto.OrderLineDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface OrderRepository {
    /** 장바구니 개수. 상단 바 배지가 본다. 장바구니를 읽거나 바꿀 때마다 갱신된다. */
    val cartCount: StateFlow<Int>

    suspend fun cart(): Result<Cart>

    suspend fun addToCart(skuId: Long, quantity: Int): Result<CartItem>

    suspend fun changeQuantity(itemId: Long, quantity: Int): Result<CartItem>

    suspend fun removeCartItem(itemId: Long): Result<Unit>

    suspend fun addresses(): Result<List<Address>>

    suspend fun createAddress(input: AddressInput): Result<Address>

    suspend fun updateAddress(id: Long, input: AddressInput): Result<Address>

    suspend fun setDefaultAddress(id: Long): Result<Address>

    suspend fun deleteAddress(id: Long): Result<Unit>

    suspend fun createOrder(addressId: Long, items: List<OrderLine>, cartItemIds: List<Long>): Result<OrderDetail>

    suspend fun orders(page: Int = 0, size: Int = 20): Result<Page<OrderSummary>>

    suspend fun order(id: Long): Result<OrderDetail>

    suspend fun cancelOrder(id: Long): Result<OrderDetail>
}

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderApi: OrderApi,
) : OrderRepository {

    private val _cartCount = MutableStateFlow(0)
    override val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

    override suspend fun cart(): Result<Cart> = safeCall { orderApi.cart().toModel() }.onSuccess { _cartCount.value = it.itemCount }

    override suspend fun addToCart(skuId: Long, quantity: Int): Result<CartItem> =
        safeCall { orderApi.addCartItem(AddCartItemRequestDto(skuId, quantity)).toModel() }.onSuccess { refreshCount() }

    override suspend fun changeQuantity(itemId: Long, quantity: Int): Result<CartItem> =
        safeCall { orderApi.changeQuantity(itemId, ChangeQuantityRequestDto(quantity)).toModel() }

    override suspend fun removeCartItem(itemId: Long): Result<Unit> = safeCall { orderApi.removeCartItem(itemId) }.onSuccess { refreshCount() }

    override suspend fun addresses(): Result<List<Address>> = safeCall { orderApi.addresses().map { it.toModel() } }

    override suspend fun createAddress(input: AddressInput): Result<Address> = safeCall { orderApi.createAddress(input.toDto()).toModel() }

    override suspend fun updateAddress(id: Long, input: AddressInput): Result<Address> = safeCall { orderApi.updateAddress(id, input.toDto()).toModel() }

    override suspend fun setDefaultAddress(id: Long): Result<Address> = safeCall { orderApi.setDefaultAddress(id).toModel() }

    override suspend fun deleteAddress(id: Long): Result<Unit> = safeCall { orderApi.deleteAddress(id) }

    override suspend fun createOrder(addressId: Long, items: List<OrderLine>, cartItemIds: List<Long>): Result<OrderDetail> =
        safeCall { orderApi.createOrder(CreateOrderRequestDto(addressId, items.map { OrderLineDto(it.skuId, it.quantity) }, cartItemIds)).toModel() }
            .onSuccess { refreshCount() }

    override suspend fun orders(page: Int, size: Int): Result<Page<OrderSummary>> = safeCall { orderApi.orders(page, size).toModel { it.toModel() } }

    override suspend fun order(id: Long): Result<OrderDetail> = safeCall { orderApi.order(id).toModel() }

    override suspend fun cancelOrder(id: Long): Result<OrderDetail> = safeCall { orderApi.cancelOrder(id).toModel() }

    private suspend fun refreshCount() {
        safeCall { orderApi.cart() }.onSuccess { _cartCount.value = it.itemCount }
    }

    private fun AddressInput.toDto() = AddressRequestDto(recipient, phone, zipCode, address1, address2, isDefault)
}
