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
    /** 결제에 쓸 포인트(1P = 1원). 0 이면 안 쓴다. 쿠폰 할인을 뺀 금액까지. */
    val usePoints: Long = 0,
    /** 쓸 쿠폰(user_coupons.id). 없으면 null. */
    val userCouponId: Long? = null,
    /** 앱이 보낸 Idempotency-Key(64자 이하). 같은 키로 다시 오면 처음 주문을 돌려준다. */
    val idempotencyKey: String? = null,
    /** 앱이 보여 준 결제 금액. 서버 계산과 다르면 409 PRICE_CHANGED. 없으면 확인하지 않는다. */
    val expectedPaymentAmount: Long? = null,
)
