package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderItem
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.Review
import java.time.LocalDateTime

data class CartItemResult(
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
    companion object {
        fun from(item: CartItem) =
            CartItemResult(
                id = requireNotNull(item.id),
                productId = requireNotNull(item.sku.product.id),
                skuId = requireNotNull(item.sku.id),
                productName = item.sku.product.name,
                optionLabel = item.sku.optionLabel(),
                imageUrl = item.sku.product.imageUrl,
                unitPrice = item.unitPrice(),
                quantity = item.quantity,
                stock = item.sku.stock,
                available = item.isAvailable(),
                lineAmount = item.lineAmount(),
            )
    }
}

/** 합계는 주문 가능한 줄(판매중·재고 충분)만 더한다. */
data class CartResult(
    val items: List<CartItemResult>,
    val totalAmount: Long,
    val itemCount: Int,
) {
    companion object {
        fun from(items: List<CartItem>): CartResult {
            val results = items.map(CartItemResult::from)
            return CartResult(
                items = results,
                totalAmount = results.filter { it.available && it.stock >= it.quantity }.sumOf { it.lineAmount },
                itemCount = results.size,
            )
        }
    }
}

data class AddressResult(
    val id: Long,
    val recipient: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val isDefault: Boolean,
) {
    companion object {
        fun from(a: Address) = AddressResult(requireNotNull(a.id), a.recipient, a.phone, a.zipCode, a.address1, a.address2, a.isDefault)
    }
}

data class OrderItemResult(
    val id: Long,
    val productId: Long,
    val skuId: Long,
    val productName: String,
    val optionLabel: String,
    val imageUrl: String?,
    val unitPrice: Long,
    val quantity: Int,
    val lineAmount: Long,
    /** 이 줄에 쓴 리뷰 id. 없으면 null. */
    val reviewId: Long?,
    /** 지금 리뷰를 쓸 수 있는가(취소 주문이 아니고 아직 안 썼음). */
    val reviewable: Boolean,
) {
    companion object {
        fun from(
            i: OrderItem,
            reviewId: Long? = null,
        ) = OrderItemResult(
            id = requireNotNull(i.id),
            productId = requireNotNull(i.product.id),
            skuId = requireNotNull(i.sku.id),
            productName = i.productName,
            optionLabel = i.optionLabel,
            imageUrl = i.imageUrl,
            unitPrice = i.unitPrice,
            quantity = i.quantity,
            lineAmount = i.lineAmount(),
            reviewId = reviewId,
            reviewable = reviewId == null && Review.canReview(i.order),
        )
    }
}

data class OrderSummaryResult(
    val id: Long,
    val orderNo: String,
    val userId: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val itemCount: Int,
    val firstItemName: String,
    val firstImageUrl: String?,
    val createdAt: LocalDateTime?,
) {
    companion object {
        fun from(o: Order): OrderSummaryResult {
            val first = o.items.firstOrNull()
            return OrderSummaryResult(
                id = requireNotNull(o.id),
                orderNo = o.orderNo,
                userId = o.userId,
                status = o.status,
                totalAmount = o.totalAmount,
                itemCount = o.items.sumOf { it.quantity },
                firstItemName = first?.productName.orEmpty(),
                firstImageUrl = first?.imageUrl,
                createdAt = o.createdAt,
            )
        }
    }
}

data class OrderDetailResult(
    val id: Long,
    val orderNo: String,
    val userId: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val paymentMethod: String,
    val recipient: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val paidAt: LocalDateTime,
    val cancelledAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val items: List<OrderItemResult>,
) {
    companion object {
        /** [reviewIds] 는 orderItemId → reviewId. 앱 주문 상세만 넘기고 어드민·생성 응답은 비워 둔다. */
        fun from(
            o: Order,
            reviewIds: Map<Long, Long> = emptyMap(),
        ) = OrderDetailResult(
            id = requireNotNull(o.id),
            orderNo = o.orderNo,
            userId = o.userId,
            status = o.status,
            totalAmount = o.totalAmount,
            paymentMethod = o.paymentMethod,
            recipient = o.recipient,
            phone = o.phone,
            zipCode = o.zipCode,
            address1 = o.address1,
            address2 = o.address2,
            paidAt = o.paidAt,
            cancelledAt = o.cancelledAt,
            createdAt = o.createdAt,
            items = o.items.map { OrderItemResult.from(it, reviewIds[it.id]) },
        )
    }
}
