package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

/** 주문을 바꾼 주체. CUSTOMER = 앱의 고객, STAFF = 어드민의 직원, SYSTEM = 배치·자동 처리. */
enum class OrderActorType { CUSTOMER, STAFF, SYSTEM }

/**
 * 주문 처리 이력 한 줄 — 상태가 바뀔 때마다 쌓인다(결제 → 배송중 → 배송완료 / 취소). 주문 상세의 타임라인이 읽고, 고객 앱도 본다.
 * [fromStatus] 가 null 이면 주문 생성(결제). 배송 시작이면 [carrier]·[trackingNo], 취소면 [reason] 이 있다. 시각은 createdAt(UTC).
 */
@Entity
@Table(name = "order_events", indexes = [Index(name = "idx_order_events_order", columnList = "order_id, id")])
class OrderEvent(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    val fromStatus: OrderStatus?,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    val toStatus: OrderStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    val actorType: OrderActorType,
    /** 고객이면 userId, 직원이면 직원 userId, 시스템이면 null. */
    @Column(name = "actor_id", length = 64)
    val actorId: String?,
    @Column(name = "reason", length = 200)
    val reason: String?,
    @Column(name = "carrier", length = 30)
    val carrier: String?,
    @Column(name = "tracking_no", length = 40)
    val trackingNo: String?,
) : BaseEntity()
