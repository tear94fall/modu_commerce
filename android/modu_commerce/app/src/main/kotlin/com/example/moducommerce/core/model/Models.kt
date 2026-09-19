package com.example.moducommerce.core.model

/** auth-service userinfo. */
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val picture: String = "",
)

data class Category(
    val id: Long,
    val name: String,
    val children: List<Category> = emptyList(),
)

data class ProductSummary(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val price: Long,
    val listPrice: Long?,
    val discountRate: Int,
    val soldOut: Boolean,
    val wished: Boolean,
)

data class OptionValue(val id: Long, val name: String)

data class OptionGroup(val id: Long, val name: String, val values: List<OptionValue>)

data class Sku(
    val id: Long,
    val optionValueIds: List<Long>,
    val optionLabel: String,
    val extraPrice: Long,
    val stock: Int,
) {
    val soldOut: Boolean get() = stock <= 0
}

data class ProductDetail(
    val id: Long,
    val name: String,
    val description: String,
    val detail: String?,
    val images: List<String>,
    val price: Long,
    val listPrice: Long?,
    val discountRate: Int,
    val soldOut: Boolean,
    val wished: Boolean,
    val wishCount: Long,
    val categoryPath: List<String>,
    val optionGroups: List<OptionGroup>,
    val skus: List<Sku>,
) {
    fun toSummary(): ProductSummary =
        ProductSummary(id, name, images.firstOrNull(), price, listPrice, discountRate, soldOut, wished)
}

/** 서버 `ProductSort.param` 과 같은 문자열. */
enum class ProductSort(val param: String) {
    LATEST("latest"),
    POPULAR("popular"),
    PRICE_ASC("priceAsc"),
    PRICE_DESC("priceDesc"),
}

data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
) {
    val hasNext: Boolean get() = number + 1 < totalPages
}

data class CartItem(
    val id: Long,
    val productId: Long,
    val skuId: Long,
    val productName: String,
    val optionLabel: String,
    val imageUrl: String?,
    val unitPrice: Long,
    val quantity: Int,
    val stock: Int,
    val available: Boolean,
    val lineAmount: Long,
) {
    /** 주문할 수 있는 줄: 판매중이고 재고가 수량 이상. */
    val orderable: Boolean get() = available && stock >= quantity
}

data class Cart(
    val items: List<CartItem>,
    val totalAmount: Long,
    val itemCount: Int,
)

data class Address(
    val id: Long,
    val recipient: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val isDefault: Boolean,
) {
    val fullAddress: String get() = listOfNotNull(address1, address2?.takeIf { it.isNotBlank() }).joinToString(" ")
}

data class AddressInput(
    val recipient: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val isDefault: Boolean,
)

enum class OrderStatus { PAID, SHIPPING, DELIVERED, CANCELLED, UNKNOWN }

data class OrderLine(val skuId: Long, val quantity: Int)

data class OrderItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val optionLabel: String,
    val imageUrl: String?,
    val unitPrice: Long,
    val quantity: Int,
    val lineAmount: Long,
)

data class OrderSummary(
    val id: Long,
    val orderNo: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val itemCount: Int,
    val firstItemName: String,
    val firstImageUrl: String?,
    val createdAt: String?,
)

data class OrderDetail(
    val id: Long,
    val orderNo: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val paymentMethod: String,
    val recipient: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val paidAt: String,
    val cancelledAt: String?,
    val items: List<OrderItem>,
) {
    val fullAddress: String get() = listOfNotNull(address1, address2?.takeIf { it.isNotBlank() }).joinToString(" ")
    val cancellable: Boolean get() = status == OrderStatus.PAID
}
