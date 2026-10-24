package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.EarnStatus
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderActorType
import com.example.commerce.application.domain.entity.OrderEvent
import com.example.commerce.application.domain.entity.OrderItem
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.PointOutbox
import com.example.commerce.application.domain.entity.PointOutboxStatus
import com.example.commerce.application.domain.entity.PointRefundStatus
import com.example.commerce.application.domain.entity.Review
import com.example.commerce.application.domain.entity.Tier
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
    val couponDiscount: Long,
    val couponName: String?,
    val pointAmount: Long,
    val paymentAmount: Long,
    val itemCount: Int,
    val firstItemName: String,
    val firstImageUrl: String?,
    val createdAt: LocalDateTime?,
    val earn: OrderEarnResult?,
    val expectedEarn: ExpectedEarnResult?,
) {
    companion object {
        /** [tier] 는 주문한 고객의 지금 등급(앱 목록). [earn] 은 [com.example.commerce.application.service.PointOutboxStatusQueryService.earnsOf] 가 읽은 값. */
        fun from(
            o: Order,
            tier: Tier?,
            earn: OrderEarnResult?,
        ): OrderSummaryResult {
            val first = o.items.firstOrNull()
            return OrderSummaryResult(
                id = requireNotNull(o.id),
                orderNo = o.orderNo,
                userId = o.userId,
                status = o.status,
                totalAmount = o.totalAmount,
                couponDiscount = o.couponDiscount,
                couponName = o.couponName,
                pointAmount = o.pointAmount,
                paymentAmount = o.paymentAmount(),
                itemCount = o.items.sumOf { it.quantity },
                firstItemName = first?.productName.orEmpty(),
                firstImageUrl = first?.imageUrl,
                createdAt = o.createdAt,
                earn = earn,
                expectedEarn = expectedEarnOf(o, tier),
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
    val couponDiscount: Long,
    val couponName: String?,
    val pointAmount: Long,
    val paymentAmount: Long,
    val paymentMethod: String,
    val recipient: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String?,
    val paidAt: LocalDateTime,
    val cancelledAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    /** 배송 시작 시각·택배사·송장번호(배송중 이후). */
    val shippedAt: LocalDateTime?,
    val carrier: String?,
    val trackingNo: String?,
    /** 취소 사유와 취소한 주체(CUSTOMER / STAFF). 취소가 아니면 null. */
    val cancelReason: String?,
    val cancelledBy: OrderActorType?,
    val items: List<OrderItemResult>,
    /** 처리 이력(오래된 것부터). 목록 응답·생성 응답은 비어 있을 수 있다. */
    val events: List<OrderEventResult> = emptyList(),
    val earn: OrderEarnResult?,
    val expectedEarn: ExpectedEarnResult?,
    /** 취소 주문의 포인트 환불 상태(아웃박스). 포인트를 안 썼거나 취소가 아니면 NONE. */
    val pointRefundStatus: PointRefundStatus = PointRefundStatus.NONE,
) {
    companion object {
        /**
         * [reviewIds] 는 orderItemId → reviewId. 앱 주문 상세만 넘기고 어드민·생성 응답은 비워 둔다.
         * [tier] 는 주문한 고객의 지금 등급(앱). 결제완료·배송중 주문의 적립 예정에 쓴다.
         * [pointRefundStatus]·[earn] 은 [com.example.commerce.application.service.PointOutboxStatusQueryService] 가 읽은 값.
         * 생성 응답은 배송 완료 전이라 [earn] 이 늘 null 이다.
         */
        fun from(
            o: Order,
            reviewIds: Map<Long, Long> = emptyMap(),
            tier: Tier? = null,
            pointRefundStatus: PointRefundStatus = PointRefundStatus.NONE,
            earn: OrderEarnResult? = null,
            events: List<OrderEvent> = emptyList(),
        ) = OrderDetailResult(
            id = requireNotNull(o.id),
            orderNo = o.orderNo,
            userId = o.userId,
            status = o.status,
            totalAmount = o.totalAmount,
            couponDiscount = o.couponDiscount,
            couponName = o.couponName,
            pointAmount = o.pointAmount,
            paymentAmount = o.paymentAmount(),
            paymentMethod = o.paymentMethod,
            recipient = o.recipient,
            phone = o.phone,
            zipCode = o.zipCode,
            address1 = o.address1,
            address2 = o.address2,
            paidAt = o.paidAt,
            cancelledAt = o.cancelledAt,
            createdAt = o.createdAt,
            deliveredAt = o.deliveredAt,
            shippedAt = o.shippedAt,
            carrier = o.carrier,
            trackingNo = o.trackingNo,
            cancelReason = o.cancelReason,
            cancelledBy = o.cancelledBy,
            items = o.items.map { OrderItemResult.from(it, reviewIds[it.id]) },
            events = events.map(OrderEventResult::from),
            earn = earn,
            expectedEarn = expectedEarnOf(o, tier),
            pointRefundStatus = pointRefundStatus,
        )
    }
}

/** 주문 생성 결과. [created] = 새로 만듦(201), false = 같은 Idempotency-Key 의 기존 주문(200). */
data class CreateOrderOutcome(
    val order: OrderDetailResult,
    val created: Boolean,
)

/**
 * 적립이 정해진 주문(배송 완료 뒤)의 적립 상태. 진행 상태는 EARN 아웃박스 [row] 가 갖는다.
 * 적립액이 있는 주문은 배송 완료와 같은 트랜잭션에 행이 생기므로 정상 경로에서는 행이 없을 수 없다. 수동 삭제 등으로 사라졌으면 PENDING 으로 보이고,
 * 원장에 적립이 없을 때만 대사(ensureEarn)가 다시 만든다(원장에 이미 있으면 그대로 PENDING 으로 남는다 — 사람이 본다).
 */
fun earnResultOf(
    o: Order,
    row: PointOutbox?,
): OrderEarnResult? {
    val points = o.earnPoints ?: return null
    val rate = o.earnRate ?: 0
    if (points <= 0) return OrderEarnResult(EarnStatus.NONE, points, rate)
    val status =
        when (row?.status) {
            PointOutboxStatus.PENDING -> EarnStatus.PENDING
            PointOutboxStatus.DONE -> EarnStatus.DONE
            PointOutboxStatus.FAILED -> EarnStatus.FAILED
            null -> EarnStatus.PENDING
        }
    return OrderEarnResult(status, points, rate)
}

/** 결제완료·배송중 주문의 적립 예정(고객의 지금 등급). 고객이 아니면 null. */
fun expectedEarnOf(
    o: Order,
    tier: Tier?,
): ExpectedEarnResult? {
    if (tier == null || (o.status != OrderStatus.PAID && o.status != OrderStatus.SHIPPING)) return null
    return ExpectedEarnResult(tier.earnFor(o.paymentAmount()), tier.earnRate)
}

/** 주문 처리 이력 한 줄. [at] 은 UTC. */
data class OrderEventResult(
    val id: Long,
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus,
    val actorType: OrderActorType,
    val actorId: String?,
    val reason: String?,
    val carrier: String?,
    val trackingNo: String?,
    val at: LocalDateTime?,
) {
    companion object {
        fun from(e: OrderEvent) =
            OrderEventResult(
                requireNotNull(e.id),
                e.fromStatus,
                e.toStatus,
                e.actorType,
                e.actorId,
                e.reason,
                e.carrier,
                e.trackingNo,
                e.createdAt,
            )
    }
}

/** 여러 주문 배송 처리의 건별 결과. 실패해도 나머지는 처리된다. */
data class ShipOrdersResult(
    val results: List<ShipOrderOutcome>,
)

data class ShipOrderOutcome(
    val orderId: Long,
    val ok: Boolean,
    val message: String?,
    val status: OrderStatus?,
)
