package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.ProductSku
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CartItemRwRepository : RwRepository<CartItem, Long> {
    fun findByUserIdAndSkuId(
        userId: String,
        skuId: Long,
    ): CartItem?

    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): CartItem?

    fun findAllByIdInAndUserId(
        ids: Collection<Long>,
        userId: String,
    ): List<CartItem>
}

interface AddressRwRepository : RwRepository<Address, Long> {
    fun findAllByUserId(userId: String): List<Address>

    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Address?
}

interface OrderRwRepository : RwRepository<Order, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Order?
}

interface ProductSkuRwRepository : RwRepository<ProductSku, Long> {
    /** 주문 생성·취소 때 재고를 고치는 동안 같은 SKU 를 다른 주문이 못 건드리게 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductSku s where s.id = :id")
    fun findByIdForUpdate(id: Long): ProductSku?
}
