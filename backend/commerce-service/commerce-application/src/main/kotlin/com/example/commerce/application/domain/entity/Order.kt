package com.example.commerce.application.domain.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 주문 상태. 결제는 모의라 생성과 동시에 PAID 다. */
enum class OrderStatus {
    PAID,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    ;

    fun canTransitionTo(next: OrderStatus): Boolean =
        when (this) {
            PAID -> next == SHIPPING || next == CANCELLED
            SHIPPING -> next == DELIVERED
            DELIVERED, CANCELLED -> false
        }
}

/** 주문 한 줄. 상품이 나중에 바뀌어도 내역이 그대로이도록 이름·옵션·사진·단가를 복사해 둔다. */
@Entity
@Table(name = "order_items")
class OrderItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    val sku: ProductSku,
    @Column(name = "product_name", nullable = false, length = 100)
    val productName: String,
    @Column(name = "option_label", nullable = false, length = 200)
    val optionLabel: String,
    @Column(name = "image_url", length = 500)
    val imageUrl: String?,
    /** 판매가 + 추가금(원). */
    @Column(name = "unit_price", nullable = false)
    val unitPrice: Long,
    @Column(name = "quantity", nullable = false)
    val quantity: Int,
) : IdentityEntity() {
    fun lineAmount(): Long = unitPrice * quantity
}

@Entity
@Table(name = "orders")
class Order(
    @Column(name = "order_no", nullable = false, unique = true, length = 20)
    val orderNo: String,
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "recipient", nullable = false, length = 30)
    val recipient: String,
    @Column(name = "phone", nullable = false, length = 20)
    val phone: String,
    @Column(name = "zip_code", nullable = false, length = 5)
    val zipCode: String,
    @Column(name = "address1", nullable = false, length = 100)
    val address1: String,
    @Column(name = "address2", length = 100)
    val address2: String?,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PAID
        protected set

    /** 상품 금액 합(원). 포인트를 빼기 전 금액. */
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long = 0
        protected set

    /** 결제에 쓴 포인트(1P = 1원). 취소하면 그대로 돌려준다. */
    @Column(name = "point_amount", nullable = false, columnDefinition = "bigint not null default 0")
    var pointAmount: Long = 0
        protected set

    /** 쿠폰 할인(원). 포인트보다 먼저 뺀다. */
    @Column(name = "coupon_discount", nullable = false, columnDefinition = "bigint not null default 0")
    var couponDiscount: Long = 0
        protected set

    /** 쓴 쿠폰(user_coupons.id). 취소하면 이것으로 돌려준다. */
    @Column(name = "user_coupon_id")
    var userCouponId: Long? = null
        protected set

    /** 주문 화면에 보일 쿠폰 이름(스냅숏). */
    @Column(name = "coupon_name", length = 40)
    var couponName: String? = null
        protected set

    @Column(name = "payment_method", nullable = false, length = 20)
    val paymentMethod: String = PAYMENT_MOCK

    @Column(name = "paid_at", nullable = false)
    var paidAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id ASC")
    val items: MutableList<OrderItem> = mutableListOf()

    /** 재고 차감은 호출자가 SKU 를 잠근 채 먼저 한다. 여기서는 스냅샷만 만든다. */
    fun addItem(
        sku: ProductSku,
        quantity: Int,
    ): OrderItem {
        val product = sku.product
        val item =
            OrderItem(
                order = this,
                product = product,
                sku = sku,
                productName = product.name,
                optionLabel = sku.optionLabel(),
                imageUrl = product.imageUrl,
                unitPrice = product.price + sku.extraPrice,
                quantity = quantity,
            )
        items.add(item)
        totalAmount += item.lineAmount()
        return item
    }

    /** 실제 결제 금액 = 상품 금액 − 쿠폰 할인 − 포인트. */
    fun paymentAmount(): Long = totalAmount - couponDiscount - pointAmount

    /** 쿠폰 적용. 상품을 다 담은 뒤, 포인트보다 먼저 부른다. */
    fun applyCoupon(
        userCouponId: Long,
        name: String,
        discount: Long,
    ) {
        require(discount in 1..totalAmount) { "쿠폰 할인액이 올바르지 않습니다." }
        this.userCouponId = userCouponId
        this.couponName = name
        this.couponDiscount = discount
    }

    /** 포인트 사용. 쿠폰을 적용한 뒤에 부른다. 남은 금액을 넘길 수 없다. */
    fun usePoints(amount: Long) {
        val payable = totalAmount - couponDiscount
        require(amount >= 0) { "사용 포인트는 0 이상이어야 합니다." }
        require(amount <= payable) { "포인트는 결제할 금액(${payable}원)까지만 쓸 수 있습니다." }
        pointAmount = amount
    }

    /** 포인트 원장의 멱등 키. 차감은 order:번호, 환불은 refund:order:번호. */
    fun pointSpendRefId(): String = "order:$orderNo"

    fun pointRefundRefId(): String = "refund:order:$orderNo"

    /** 사용자 취소. 결제 완료 상태에서만. 재고 복구는 호출자가 한다. */
    fun cancel(now: LocalDateTime = LocalDateTime.now()) {
        transition(OrderStatus.CANCELLED, now)
    }

    /** 관리자 상태 변경. 허용된 전이만. */
    fun transition(
        next: OrderStatus,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        require(status.canTransitionTo(next)) { "${status.label()} 상태에서 ${next.label()} 로 바꿀 수 없습니다." }
        status = next
        if (next == OrderStatus.CANCELLED) cancelledAt = now
    }

    fun isCancelled(): Boolean = status == OrderStatus.CANCELLED

    companion object {
        const val PAYMENT_MOCK = "MOCK"
        private val random = SecureRandom()
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** `20260919-4F7K2A`. 날짜 + 32진 6자리 난수(1,073,741,824 가지). 유니크 제약이 최종 방어다. */
        fun newOrderNo(now: LocalDateTime = LocalDateTime.now()): String =
            now.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        fun create(
            userId: String,
            address: Address,
            now: LocalDateTime = LocalDateTime.now(),
        ): Order =
            Order(
                orderNo = newOrderNo(now),
                userId = userId,
                recipient = address.recipient,
                phone = address.phone,
                zipCode = address.zipCode,
                address1 = address.address1,
                address2 = address.address2,
            )
    }
}

fun OrderStatus.label(): String =
    when (this) {
        OrderStatus.PAID -> "결제완료"
        OrderStatus.SHIPPING -> "배송중"
        OrderStatus.DELIVERED -> "배송완료"
        OrderStatus.CANCELLED -> "취소"
    }
