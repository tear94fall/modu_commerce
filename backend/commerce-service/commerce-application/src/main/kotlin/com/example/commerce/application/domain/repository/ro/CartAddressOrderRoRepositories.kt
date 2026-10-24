package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderEvent
import com.example.commerce.application.domain.entity.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

// 장바구니·배송지는 조회도 master 에서 한다(쓴 직후 다시 읽는 화면이라) — rw 저장소를 쓴다.

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

    /**
     * 백오피스 통계용 — 결제 시각이 [from, to) 거나 취소 시각이 [from, to) 인 주문의 [paidAt, status, totalAmount, couponDiscount, pointAmount,
     * userCouponId, cancelledAt]. 시각은 UTC. 날짜로 묶는 일은 호출 쪽(DayWindow)이 한다.
     */
    @Query(
        "select o.paidAt, o.status, o.totalAmount, o.couponDiscount, o.pointAmount, o.userCouponId, o.cancelledAt from Order o " +
            "where (o.paidAt >= :from and o.paidAt < :to) or (o.cancelledAt >= :from and o.cancelledAt < :to)",
    )
    fun findStatsRowsBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<Array<Any?>>

    /** 이 쿠폰을 쓴 주문의 [status, totalAmount, couponDiscount, pointAmount] — 백오피스 쿠폰 성과. */
    @Query(
        "select o.status, o.totalAmount, o.couponDiscount, o.pointAmount from Order o " +
            "where o.userCouponId in (select uc.id from UserCoupon uc where uc.coupon.id = :couponId)",
    )
    fun findCouponOrderRows(
        @Param("couponId") couponId: Long,
    ): List<Array<Any?>>

    /**
     * 기획전 상품이 들어간 주문 줄 — 결제 시각이 [from, to)(UTC)인 주문의 [orderId, status, productId, productName, quantity, unitPrice].
     * 백오피스 기획전 성과.
     */
    @Query(
        "select o.id, o.status, i.product.id, i.productName, i.quantity, i.unitPrice from OrderItem i join i.order o " +
            "where i.product.id in :productIds and o.paidAt >= :from and o.paidAt < :to",
    )
    fun findPromotionItemRows(
        @Param("productIds") productIds: Collection<Long>,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<Array<Any?>>

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

    /** 적립 대사 대상: 적립액이 있는 주문 중 [from, to)(UTC)에 배송 완료된 것. delivered_at 에 인덱스는 없다(하루 한 번 전날 범위). */
    @Query("select o from Order o where o.earnPoints > 0 and o.deliveredAt >= :from and o.deliveredAt < :to order by o.id")
    fun findEarnOrdersBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<Order>

    /** 포인트 대사 대상: 포인트를 쓴 주문 중 [from, to)(UTC)에 만들었거나 취소한 것. */
    @Query(
        "select o from Order o where o.pointAmount > 0 and " +
            "((o.createdAt >= :from and o.createdAt < :to) or (o.cancelledAt >= :from and o.cancelledAt < :to)) order by o.id",
    )
    fun findPointOrdersBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<Order>

    /** 주문 줄 id 로 내 주문을 찾는다(리뷰 대상 확인). */
    @Query("select o from Order o join o.items i where i.id = :itemId and o.userId = :userId")
    fun findByItemIdAndUserId(
        itemId: Long,
        userId: String,
    ): Order?
}

/** 주문 처리 이력 읽기(replica) — 주문 상세 타임라인. */
interface OrderEventRoRepository : RoRepository<OrderEvent, Long> {
    fun findAllByOrderIdOrderByIdAsc(orderId: Long): List<OrderEvent>
}
