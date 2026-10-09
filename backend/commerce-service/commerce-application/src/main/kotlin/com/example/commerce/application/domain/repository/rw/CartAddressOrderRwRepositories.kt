package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.ProductSku
import com.example.commerce.application.domain.repository.ro.UserAmount
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
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

/**
 * 행 잠금 대기 한도(ms). 오래 막히면 잠금 실패(→ 503 "요청이 몰려…")로 끝낸다.
 * Hibernate 의 MySQL 방언은 이 힌트를 SQL 에 싣지 않으므로(MySQL 에 WAIT n 문법이 없다) 운영 DB 는 커넥션의
 * innodb_lock_wait_timeout(application.yml 의 hikari connection-init-sql)이 같은 값으로 막는다.
 */
const val LOCK_TIMEOUT_MS = "3000"
const val LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout"

interface OrderRwRepository : RwRepository<Order, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Order?

    /** 사용자 취소. 내 주문 행을 잠근다(남의 것·없는 것은 null → 404). 잠금 순서: 주문 → SKU(id 순). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = LOCK_TIMEOUT_HINT, value = LOCK_TIMEOUT_MS))
    @Query("select o from Order o where o.id = :id and o.userId = :userId")
    fun findByIdAndUserIdForUpdate(
        @Param("id") id: Long,
        @Param("userId") userId: String,
    ): Order?

    /** 같은 회원·같은 Idempotency-Key 로 이미 만든 주문. */
    fun findByUserIdAndIdempotencyKey(
        userId: String,
        idempotencyKey: String,
    ): Order?

    fun existsByOrderNo(orderNo: String): Boolean

    fun findByOrderNo(orderNo: String): Order?

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

    /** 관리자 상태 변경·구매 적립. 주문 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = LOCK_TIMEOUT_HINT, value = LOCK_TIMEOUT_MS))
    @Query("select o from Order o where o.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Order?
}

interface ProductSkuRwRepository : RwRepository<ProductSku, Long> {
    /** 주문 생성·취소 때 재고를 고치는 동안 같은 SKU 를 다른 주문이 못 건드리게 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = LOCK_TIMEOUT_HINT, value = LOCK_TIMEOUT_MS))
    @Query("select s from ProductSku s where s.id = :id")
    fun findByIdForUpdate(id: Long): ProductSku?

    /**
     * 지금 DB 의 재고를 잠근 채 읽는다(관리자 상품 저장의 재고 증감). 이미 영속성 컨텍스트에 있는 SKU 는 [findByIdForUpdate] 가
     * 옛값을 돌려주므로 값만 따로 읽는다. 잠금 대기 한도는 커넥션의 innodb_lock_wait_timeout 이 막는다.
     */
    @Query(value = "select stock from product_skus where id = :id for update", nativeQuery = true)
    fun lockStock(
        @Param("id") id: Long,
    ): Int?
}
