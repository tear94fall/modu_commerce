package com.example.moducommerce.testing

import com.example.moducommerce.core.model.Address
import com.example.moducommerce.core.model.AddressInput
import com.example.moducommerce.core.model.Cart
import com.example.moducommerce.core.model.CartItem
import com.example.moducommerce.core.model.OrderDetail
import com.example.moducommerce.core.model.OrderLine
import com.example.moducommerce.core.model.OrderStatus
import com.example.moducommerce.core.model.OrderSummary
import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.network.ApiException
import com.example.moducommerce.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun cartItem(id: Long, skuId: Long = id * 10, quantity: Int = 1, stock: Int = 10, available: Boolean = true, unitPrice: Long = 10_000) =
    CartItem(id, productId = id * 100, skuId = skuId, productName = "상품 $id", optionLabel = "", imageUrl = null, unitPrice = unitPrice, quantity = quantity, stock = stock, available = available, lineAmount = unitPrice * quantity)

fun address(id: Long, isDefault: Boolean = false) = Address(id, "임", "010-0000-0000", "06236", "서울", null, isDefault)

fun orderDetail(id: Long = 1, status: OrderStatus = OrderStatus.PAID) =
    OrderDetail(id, "20260919-ABCDEF", status, 20_000, "MOCK", "임", "010", "06236", "서울", null, "2026-09-19T05:00:00", null, emptyList())

class FakeOrderRepository : OrderRepository {
    var cartItems = mutableListOf<CartItem>()
    var addressList = mutableListOf<Address>()
    val quantityCalls = mutableListOf<Pair<Long, Int>>()
    val removeCalls = mutableListOf<Long>()
    var quantityResult: Result<Unit> = Result.success(Unit)
    var createOrderResult: Result<OrderDetail> = Result.success(orderDetail())
    var lastCreate: Triple<Long, List<OrderLine>, List<Long>>? = null
    var fail = false

    override val cartCount: StateFlow<Int> = MutableStateFlow(0)

    override suspend fun cart(): Result<Cart> = if (fail) Result.failure(RuntimeException("boom")) else Result.success(Cart(cartItems.toList(), 0, cartItems.size))

    override suspend fun addToCart(skuId: Long, quantity: Int): Result<CartItem> = Result.success(cartItem(99, skuId, quantity))

    override suspend fun changeQuantity(itemId: Long, quantity: Int): Result<CartItem> {
        quantityCalls += itemId to quantity
        return quantityResult.map { cartItems.first { it.id == itemId }.copy(quantity = quantity) }
    }

    override suspend fun removeCartItem(itemId: Long): Result<Unit> {
        removeCalls += itemId
        return Result.success(Unit)
    }

    override suspend fun addresses(): Result<List<Address>> = Result.success(addressList.toList())

    override suspend fun createAddress(input: AddressInput): Result<Address> {
        val created = Address((addressList.size + 1).toLong(), input.recipient, input.phone, input.zipCode, input.address1, input.address2, input.isDefault || addressList.isEmpty())
        addressList += created
        return Result.success(created)
    }

    override suspend fun updateAddress(id: Long, input: AddressInput): Result<Address> = Result.success(address(id))

    override suspend fun setDefaultAddress(id: Long): Result<Address> = Result.success(address(id, true))

    override suspend fun deleteAddress(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun createOrder(addressId: Long, items: List<OrderLine>, cartItemIds: List<Long>): Result<OrderDetail> {
        lastCreate = Triple(addressId, items, cartItemIds)
        return createOrderResult
    }

    override suspend fun orders(page: Int, size: Int): Result<Page<OrderSummary>> = Result.success(Page(emptyList(), 0, 0, 0))

    override suspend fun order(id: Long): Result<OrderDetail> = Result.success(orderDetail(id))

    override suspend fun cancelOrder(id: Long): Result<OrderDetail> = Result.success(orderDetail(id, OrderStatus.CANCELLED))

    companion object {
        fun badRequest(message: String) = ApiException(400, "{\"message\":\"$message\"}")
    }
}
