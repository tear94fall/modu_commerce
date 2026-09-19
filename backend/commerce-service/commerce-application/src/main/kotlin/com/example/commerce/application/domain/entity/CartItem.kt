package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** 장바구니 한 줄 = SKU 하나 + 수량. 같은 SKU 를 다시 담으면 수량을 더한다. */
@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = [UniqueConstraint(name = "uk_cart_items_user_sku", columnNames = ["user_id", "sku_id"])],
)
class CartItem(
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    val sku: ProductSku,
    @Column(name = "quantity", nullable = false)
    var quantity: Int,
) : BaseEntity() {
    fun add(amount: Int) {
        changeQuantity(quantity + amount)
    }

    fun changeQuantity(next: Int) {
        require(next in 1..MAX_QUANTITY) { "수량은 1~${MAX_QUANTITY} 사이여야 합니다." }
        quantity = next
    }

    /** 담은 뒤 상품이 숨김·삭제되면 주문할 수 없다. 화면은 이 줄을 회색으로 보여 준다. */
    fun isAvailable(): Boolean = !sku.product.isDeleted() && sku.product.status == ProductStatus.SELLING

    fun unitPrice(): Long = sku.product.price + sku.extraPrice

    fun lineAmount(): Long = unitPrice() * quantity

    companion object {
        const val MAX_QUANTITY = 99
    }
}
