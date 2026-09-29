package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.ProductSku
import com.example.commerce.application.domain.repository.ro.UserAmount
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CartItemRwRepository : RwRepository<CartItem, Long> {
    /** 내 장바구니(최근 담은 것부터). 조회도 master 에서 한다 — CartQueryService 참고. */
    fun findAllByUserIdOrderByIdDesc(userId: String): List<CartItem>

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

    /** 내 배송지(기본 먼저, 최근 것부터). 조회도 master 에서 한다 — AddressQueryService 참고. */
    fun findAllByUserIdOrderByIsDefaultDescIdDesc(userId: String): List<Address>

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

    /** 배송 완료 결제 금액(상품 − 쿠폰 − 포인트)의 회원별 합. 기간은 [from, to) UTC. 취소 주문은 상태가 달라 빠진다. */
    @Query(
        "select new com.example.commerce.application.domain.repository.ro.UserAmount(o.userId, " +
            "sum(o.totalAmount - o.couponDiscount - o.pointAmount)) " +
            "from Order o where o.status = com.example.commerce.application.domain.entity.OrderStatus.DELIVERED " +
            "and o.deliveredAt >= :from and o.deliveredAt < :to group by o.userId",
    )
    fun sumDelivered(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<UserAmount>

    @Query(
        "select new com.example.commerce.application.domain.repository.ro.UserAmount(o.userId, " +
            "sum(o.totalAmount - o.couponDiscount - o.pointAmount)) " +
            "from Order o where o.status = com.example.commerce.application.domain.entity.OrderStatus.DELIVERED " +
            "and o.deliveredAt >= :from and o.deliveredAt < :to and o.userId in :userIds group by o.userId",
    )
    fun sumDeliveredOf(
        @Param("userIds") userIds: Collection<String>,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<UserAmount>

    /** 적립 재시도 대상. */
    @Query("select o.id from Order o where o.earnStatus = com.example.commerce.application.domain.entity.EarnStatus.PENDING order by o.id")
    fun findPendingEarnIds(): List<Long>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Order?
}

interface ProductSkuRwRepository : RwRepository<ProductSku, Long> {
    /** 주문 생성·취소 때 재고를 고치는 동안 같은 SKU 를 다른 주문이 못 건드리게 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductSku s where s.id = :id")
    fun findByIdForUpdate(id: Long): ProductSku?
}
