package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CartItemRoRepository : RoRepository<CartItem, Long> {
    fun findAllByUserIdOrderByIdDesc(userId: String): List<CartItem>
}

interface AddressRoRepository : RoRepository<Address, Long> {
    fun findAllByUserIdOrderByIsDefaultDescIdDesc(userId: String): List<Address>

    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Address?
}

/** 회원별 금액 한 줄(등급 기준 금액). */
data class UserAmount(
    val userId: String,
    val amount: Long?,
)

/** 회원 한 명의 주문 상태별 개수·결제 금액 합·마지막 주문 시각(백오피스 회원 요약). */
data class OrderStatusStat(
    val status: OrderStatus,
    val count: Long,
    val paymentAmount: Long?,
    val lastCreatedAt: LocalDateTime?,
)

interface OrderCustomRepository {
    /** 어드민 목록. 상태·주문번호(부분 일치)로 거른다. 최신부터. */
    fun searchAdminPage(
        status: OrderStatus?,
        orderNo: String?,
        pageable: Pageable,
    ): Page<Order>
}

interface OrderRoRepository :
    RoRepository<Order, Long>,
    OrderCustomRepository {
    fun findAllByUserIdOrderByIdDesc(
        userId: String,
        pageable: Pageable,
    ): Page<Order>

    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Order?

    fun findById(id: Long): Order?

    /** 배송 완료 결제 금액(상품 − 쿠폰 − 포인트)의 회원별 합. 기간은 [from, to) UTC. */
    @Query(
        "select new com.example.commerce.application.domain.repository.ro.UserAmount(o.userId, " +
            "sum(o.totalAmount - o.couponDiscount - o.pointAmount)) " +
            "from Order o where o.status = com.example.commerce.application.domain.entity.OrderStatus.DELIVERED " +
            "and o.deliveredAt >= :from and o.deliveredAt < :to and o.userId in :userIds group by o.userId",
    )
    fun sumDelivered(
        @Param("userIds") userIds: Collection<String>,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<UserAmount>

    /** 회원 한 명의 주문을 상태별로 묶는다. 결제 금액은 상품 − 쿠폰 − 포인트. 주문이 없는 상태는 빠진다. */
    @Query(
        "select new com.example.commerce.application.domain.repository.ro.OrderStatusStat(o.status, count(o), " +
            "sum(o.totalAmount - o.couponDiscount - o.pointAmount), max(o.createdAt)) " +
            "from Order o where o.userId = :userId group by o.status",
    )
    fun statsByStatus(
        @Param("userId") userId: String,
    ): List<OrderStatusStat>

    /** 주문 줄 id 로 내 주문을 찾는다(리뷰 대상 확인). */
    @Query("select o from Order o join o.items i where i.id = :itemId and o.userId = :userId")
    fun findByItemIdAndUserId(
        itemId: Long,
        userId: String,
    ): Order?
}
