package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 옵션 조합 하나 = 재고 단위. 그룹마다 값 하나씩을 가리키고, 옵션 없는 상품은 값 없는 SKU 하나다.
 * 장바구니·주문은 이 id 를 가리키므로, 상품을 수정해도 같은 조합의 SKU 는 id 를 유지한다.
 */
@Entity
@Table(name = "product_skus")
class ProductSku(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "sku_option_values",
        joinColumns = [JoinColumn(name = "sku_id")],
        inverseJoinColumns = [JoinColumn(name = "option_value_id")],
    )
    val optionValues: MutableSet<ProductOptionValue> = mutableSetOf(),
    /** 판매가에 더해지는 금액(원). */
    @Column(name = "extra_price", nullable = false)
    var extraPrice: Long = 0,
    @Column(name = "stock", nullable = false)
    var stock: Int = 0,
) : IdentityEntity() {
    fun update(
        extraPrice: Long,
        stock: Int,
    ) {
        this.extraPrice = extraPrice
        this.stock = stock
    }

    fun isSoldOut(): Boolean = stock <= 0

    /** 주문 생성. 모자라면 IllegalArgumentException(→ 400). 호출자가 행을 잠근 채 부른다. */
    fun decreaseStock(quantity: Int) {
        require(quantity > 0) { "수량은 1 이상이어야 합니다." }
        require(stock >= quantity) { "재고가 부족합니다: ${displayName()} (남은 수량 $stock)" }
        stock -= quantity
    }

    /** 주문 취소. */
    fun increaseStock(quantity: Int) {
        require(quantity > 0) { "수량은 1 이상이어야 합니다." }
        stock += quantity
    }

    /** "상품명 (블랙 / M)" 또는 옵션이 없으면 "상품명". */
    fun displayName(): String = optionLabel().let { if (it.isEmpty()) product.name else "${product.name} ($it)" }

    /** 그룹명=값명 을 그룹명 순으로 이은 조합 키. 상품 수정 때 같은 조합을 찾는 데 쓴다. */
    fun optionKey(): String = optionKeyOf(optionValues.associate { it.group.name to it.name })

    /** 화면용 "블랙 / M". 옵션이 없으면 빈 문자열. */
    fun optionLabel(): String = optionValues.sortedBy { it.group.sortOrder }.joinToString(" / ") { it.name }

    companion object {
        fun optionKeyOf(options: Map<String, String>): String =
            options.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
    }
}
