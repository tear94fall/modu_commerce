package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.LocalDateTime

/**
 * 포인트 보상 작업의 종류.
 * - SPEND_GUARD: 주문 결제에서 포인트를 차감하기 "전에" 남기는 표시. 주문이 커밋되면 DONE, 커밋되지 않았으면(롤백·장애)
 *   릴레이가 주문이 없는 것을 보고 point-service 에 차감 취소(spend/cancel)를 보낸다.
 * - REFUND: 주문 취소의 포인트 환불. 취소와 같은 트랜잭션에 넣고, 커밋 뒤 바로 한 번 보낸다. 실패하면 릴레이가 다시 보낸다.
 */
enum class PointOutboxKind { SPEND_GUARD, REFUND }

/** PENDING = 보낼 것(또는 확인할 것), DONE = 끝, FAILED = 사람이 봐야 함(재시도 소진·이상 응답). */
enum class PointOutboxStatus { PENDING, DONE, FAILED }

/**
 * 포인트 아웃박스 한 줄. [refId] 는 원래 차감 키(`order:<주문번호>`)라 종류마다 한 줄뿐이다(kind+ref_id 유니크).
 * 시각은 모두 UTC. 스키마는 DBA 가 만든다(modu_infra data/mysql/schema/changes/2026-10-09-point-outbox.sql).
 */
@Entity
@Table(
    name = "point_outbox",
    uniqueConstraints = [UniqueConstraint(name = "uk_point_outbox_kind_ref", columnNames = ["kind", "ref_id"])],
    indexes = [
        Index(name = "idx_point_outbox_status_next", columnList = "status, next_attempt_at"),
        Index(name = "idx_point_outbox_order_no", columnList = "order_no"),
    ],
)
class PointOutbox(
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    val kind: PointOutboxKind,
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "order_no", nullable = false, length = 20)
    val orderNo: String,
    /** 원래 차감 키 `order:<주문번호>`. 취소(spend/cancel)도 이 키로 보낸다. */
    @Column(name = "ref_id", nullable = false, length = 128)
    val refId: String,
    @Column(name = "amount", nullable = false)
    val amount: Long,
    nextAttemptAt: LocalDateTime,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: PointOutboxStatus = PointOutboxStatus.PENDING
        protected set

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0
        protected set

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: LocalDateTime = nextAttemptAt
        protected set

    @Column(name = "last_error", length = 300)
    var lastError: String? = null
        protected set

    @Column(name = "done_at")
    var doneAt: LocalDateTime? = null
        protected set

    fun isPending(): Boolean = status == PointOutboxStatus.PENDING

    fun done(now: LocalDateTime) {
        status = PointOutboxStatus.DONE
        doneAt = now
    }

    /** 다시 보내도 소용없는 경우(이상 응답). 사람이 본다. */
    fun failed(error: String) {
        status = PointOutboxStatus.FAILED
        lastError = error.take(MAX_ERROR)
    }

    /**
     * 한 번 실패. 1분, 2분, 4분 … 최대 1시간 뒤에 다시 보낸다. [MAX_ATTEMPTS] 번 실패하면 FAILED.
     * @return FAILED 가 됐으면 true
     */
    fun retryLater(
        error: String,
        now: LocalDateTime,
    ): Boolean {
        attempts += 1
        lastError = error.take(MAX_ERROR)
        if (attempts >= MAX_ATTEMPTS) {
            status = PointOutboxStatus.FAILED
            return true
        }
        nextAttemptAt = now.plus(backoff(attempts))
        return false
    }

    companion object {
        const val MAX_ATTEMPTS = 20
        const val MAX_ERROR = 300
        private val FIRST_BACKOFF: Duration = Duration.ofMinutes(1)
        private val MAX_BACKOFF: Duration = Duration.ofHours(1)

        /** [attempts] 번째 실패 뒤 기다릴 시간. 1분 × 2^(attempts-1), 최대 1시간. */
        fun backoff(attempts: Int): Duration {
            val shift = (attempts - 1).coerceIn(0, 10)
            val delay = FIRST_BACKOFF.multipliedBy(1L shl shift)
            return if (delay > MAX_BACKOFF) MAX_BACKOFF else delay
        }

        fun spendGuard(
            order: Order,
            nextAttemptAt: LocalDateTime,
        ) = PointOutbox(PointOutboxKind.SPEND_GUARD, order.userId, order.orderNo, order.pointSpendRefId(), order.pointAmount, nextAttemptAt)

        fun refund(
            order: Order,
            nextAttemptAt: LocalDateTime,
        ) = PointOutbox(PointOutboxKind.REFUND, order.userId, order.orderNo, order.pointSpendRefId(), order.pointAmount, nextAttemptAt)
    }
}

/**
 * 주문 응답의 포인트 환불 상태. NONE = 포인트를 안 썼거나 취소가 아님, PENDING = 환불 보내는 중(재시도 포함),
 * DONE = 돌려줌, FAILED = 사람이 봐야 함. 아웃박스 이전에 취소된 주문(행 없음)은 취소와 함께 환불됐으므로 DONE.
 */
enum class PointRefundStatus {
    NONE,
    PENDING,
    DONE,
    FAILED,
    ;

    companion object {
        fun of(
            order: Order,
            refundRow: PointOutbox?,
        ): PointRefundStatus {
            if (order.pointAmount <= 0 || !order.isCancelled()) return NONE
            return when (refundRow?.status) {
                null, PointOutboxStatus.DONE -> DONE
                PointOutboxStatus.PENDING -> PENDING
                PointOutboxStatus.FAILED -> FAILED
            }
        }
    }
}
