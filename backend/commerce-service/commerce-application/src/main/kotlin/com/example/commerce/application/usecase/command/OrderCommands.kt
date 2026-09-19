package com.example.commerce.application.usecase.command

data class AddressCommand(
    val recipient: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val isDefault: Boolean,
)

data class OrderLineCommand(
    val skuId: Long,
    val quantity: Int,
)

/** [cartItemIds] 는 주문이 성공하면 장바구니에서 지울 줄. 바로 구매면 비어 있다. */
data class CreateOrderCommand(
    val addressId: Long,
    val items: List<OrderLineCommand>,
    val cartItemIds: List<Long>,
)
