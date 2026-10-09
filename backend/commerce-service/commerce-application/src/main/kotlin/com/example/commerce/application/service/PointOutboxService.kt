package com.example.commerce.application.service

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.PointOutbox
import com.example.commerce.application.domain.entity.PointOutboxKind
import com.example.commerce.application.domain.entity.PointOutboxStatus
import com.example.commerce.application.domain.entity.PointRefundStatus
import com.example.commerce.application.domain.repository.ro.PointOutboxRoRepository
import com.example.commerce.application.domain.repository.rw.OrderRwRepository
import com.example.commerce.application.domain.repository.rw.PointOutboxRwRepository
import com.example.commerce.application.point.PointCancelResult
import com.example.commerce.application.point.PointGateway
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/** 아웃박스 한 줄을 point-service 에 보낼 때 필요한 값(트랜잭션 밖에서 쓴다). */
data class PointOutboxTask(
    val id: Long,
    val kind: PointOutboxKind,
    val userId: String,
    val orderNo: String,
    val refId: String,
    val amount: Long,
) {
    companion object {
        fun of(row: PointOutbox) = PointOutboxTask(requireNotNull(row.id), row.kind, row.userId, row.orderNo, row.refId, row.amount)
    }
}

/**
 * 아웃박스의 DB 단계. 기본은 늘 새 트랜잭션(REQUIRES_NEW): 주문 트랜잭션이 롤백돼도 남아야 하는 표시(SPEND_GUARD)와
 * 커밋 뒤(afterCommit)의 상태 변경이 여기서 따로 커밋된다. 취소 환불(REFUND)만 취소와 같은 트랜잭션에 넣는다(MANDATORY).
 */
@Service
@Transactional(transactionManager = "rwTransactionManager", propagation = Propagation.REQUIRES_NEW)
class PointOutboxCommandService(
    private val pointOutboxRwRepository: PointOutboxRwRepository,
    private val clock: Clock,
) {
    /** 차감 직전의 표시. 주문이 커밋되지 않으면 [SPEND_GUARD_DELAY] 뒤 릴레이가 차감을 되돌린다. */
    fun guard(order: Order): Long {
        val row = PointOutbox.spendGuard(order, now().plus(SPEND_GUARD_DELAY))
        return requireNotNull(pointOutboxRwRepository.saveAndFlush(row).id)
    }

    /** 취소 환불. 취소와 같은 트랜잭션에서 넣는다(취소가 커밋되면 반드시 남는다). 바로 보낼 수 있게 next_attempt_at = 지금. */
    @Transactional(transactionManager = "rwTransactionManager", propagation = Propagation.MANDATORY)
    fun enqueueRefund(order: Order): Long {
        pointOutboxRwRepository.findByKindAndRefId(PointOutboxKind.REFUND, order.pointSpendRefId())?.let { return requireNotNull(it.id) }
        return requireNotNull(pointOutboxRwRepository.saveAndFlush(PointOutbox.refund(order, now())).id)
    }

    /**
     * 대사가 찾은 "환불 없는 취소 주문" 에 REFUND 행이 없으면 만든다.
     * @return 지금 있는 행의 상태(새로 만들었으면 null)
     */
    fun ensureRefund(
        userId: String,
        orderNo: String,
        refId: String,
        amount: Long,
    ): PointOutboxStatus? {
        pointOutboxRwRepository.findByKindAndRefId(PointOutboxKind.REFUND, refId)?.let { return it.status }
        pointOutboxRwRepository.saveAndFlush(PointOutbox(PointOutboxKind.REFUND, userId, orderNo, refId, amount, now()))
        return null
    }

    /** 보낼 차례인 PENDING(최대 100건, 오래된 순). */
    fun due(): List<PointOutboxTask> =
        pointOutboxRwRepository
            .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(PointOutboxStatus.PENDING, now())
            .map(PointOutboxTask::of)

    fun pendingTask(id: Long): PointOutboxTask? =
        pointOutboxRwRepository
            .findByIdOrNull(id)
            ?.takeIf {
                it.isPending()
            }?.let(PointOutboxTask::of)

    fun done(id: Long) {
        pointOutboxRwRepository.findByIdOrNull(id)?.takeIf { it.isPending() }?.done(now())
    }

    fun failed(
        id: Long,
        error: String,
    ) {
        pointOutboxRwRepository.findByIdOrNull(id)?.takeIf { it.isPending() }?.failed(error)
    }

    /** @return 재시도를 다 써서 FAILED 가 됐으면 true */
    fun retryLater(
        id: Long,
        error: String,
    ): Boolean = pointOutboxRwRepository.findByIdOrNull(id)?.takeIf { it.isPending() }?.retryLater(error, now()) ?: false

    private fun now(): LocalDateTime = TierPeriods.utcNow(clock)

    companion object {
        /** 주문 트랜잭션이 이보다 오래 걸리지 않는다(잠금 3초·포인트 호출 3초). 이 시간이 지나도 주문이 없으면 커밋되지 않은 것. */
        val SPEND_GUARD_DELAY: Duration = Duration.ofMinutes(2)
    }
}

/** 주문 응답의 포인트 환불 상태. 레플리카(조회 화면) 또는 master(취소 직후 응답)에서 읽는다. */
@Service
class PointRefundStatusQueryService(
    private val pointOutboxRoRepository: PointOutboxRoRepository,
    private val pointOutboxRwRepository: PointOutboxRwRepository,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun of(order: Order): PointRefundStatus =
        if (order.pointAmount <= 0 || !order.isCancelled()) {
            PointRefundStatus.NONE
        } else {
            PointRefundStatus.of(order, pointOutboxRoRepository.findByKindAndRefId(PointOutboxKind.REFUND, order.pointSpendRefId()))
        }

    @Transactional(transactionManager = "rwTransactionManager", readOnly = true)
    fun ofMaster(order: Order): PointRefundStatus =
        if (order.pointAmount <= 0 || !order.isCancelled()) {
            PointRefundStatus.NONE
        } else {
            PointRefundStatus.of(order, pointOutboxRwRepository.findByKindAndRefId(PointOutboxKind.REFUND, order.pointSpendRefId()))
        }
}

/**
 * 포인트 아웃박스. 주문 결제·취소와 point-service 사이의 어긋남을 메운다.
 *
 * - 결제: [guardSpend] 가 차감 전에 SPEND_GUARD 를 따로 커밋하고, 주문이 커밋되면 DONE 으로 바꾼다. 주문이 롤백되면(차감 뒤 커밋 실패 등)
 *   PENDING 으로 남고, 2분 뒤 [relay] 가 주문이 없는 것을 보고 차감을 되돌린다(spend/cancel).
 * - 취소: [refundOnCancel] 이 취소 트랜잭션 안에 REFUND 를 넣고, 커밋 뒤 바로 한 번 보낸다. point-service 가 죽어 있어도 취소는 커밋되고
 *   [relay] 가 1분, 2분, 4분 … 최대 1시간 간격으로 다시 보낸다(20번 실패하면 FAILED).
 */
@Service
class PointOutboxService(
    private val commandService: PointOutboxCommandService,
    private val orderRwRepository: OrderRwRepository,
    private val pointGateway: PointGateway,
) {
    /** 주문 트랜잭션 안에서, 차감을 부르기 직전에. */
    fun guardSpend(order: Order) {
        val id = commandService.guard(order)
        afterCommit { commandService.done(id) }
    }

    /** 취소 트랜잭션 안에서. 커밋 뒤 바로 한 번 보낸다(실패하면 릴레이가 이어 받는다). */
    fun refundOnCancel(order: Order) {
        val id = commandService.enqueueRefund(order)
        afterCommit { commandService.pendingTask(id)?.let { send(it) } }
    }

    /** 보낼 차례인 줄을 보낸다. 끝낸(DONE) 수를 돌려준다. */
    fun relay(): Int = commandService.due().count { send(it) }

    /** 한 줄을 처리한다. DONE 이 되면 true. 실패는 재시도로 남기고 예외를 던지지 않는다. */
    fun send(task: PointOutboxTask): Boolean =
        try {
            when (task.kind) {
                PointOutboxKind.SPEND_GUARD -> settleGuard(task)
                PointOutboxKind.REFUND -> refund(task)
            }
        } catch (e: Exception) {
            val error = "${e.javaClass.simpleName}: ${e.message}"
            if (commandService.retryLater(task.id, error)) {
                logger.error(e) { "point outbox ${task.kind} ${task.refId} FAILED after retries: $error" }
            } else {
                logger.warn { "point outbox ${task.kind} ${task.refId} retry later: $error" }
            }
            false
        }

    /** 주문이 있으면(커밋됨) 끝. 없으면 커밋되지 않은 주문의 차감이 남았을 수 있으니 되돌린다(차감이 없었으면 NO_SPEND 로 끝). */
    private fun settleGuard(task: PointOutboxTask): Boolean {
        if (orderRwRepository.existsByOrderNo(task.orderNo)) {
            commandService.done(task.id)
            return true
        }
        val result = pointGateway.cancelSpend(task.userId, task.refId, "주문 미완료 포인트 복구 ${task.orderNo}")
        if (result.cancelled) {
            logger.warn { "orphan spend cancelled: ${task.refId} user=${task.userId} amount=${result.amount}" }
        }
        commandService.done(task.id)
        return true
    }

    /** 취소 환불. 차감이 없다는 답(NO_SPEND)은 있을 수 없는 일이라 FAILED 로 두고 사람이 본다. */
    private fun refund(task: PointOutboxTask): Boolean {
        val result = pointGateway.cancelSpend(task.userId, task.refId, "주문 취소 ${task.orderNo}")
        if (!result.cancelled && result.reason == PointCancelResult.NO_SPEND) {
            commandService.failed(task.id, "NO_SPEND: point-service has no spend ${task.refId}")
            logger.error { "point refund anomaly: no spend for cancelled order ${task.orderNo} (${task.refId}, ${task.amount}P)" }
            return false
        }
        commandService.done(task.id)
        return true
    }

    private fun afterCommit(action: () -> Unit) {
        val safe = {
            try {
                action()
            } catch (e: Exception) {
                // 커밋은 이미 끝났다. 여기서 던지면 호출자가 실패로 오해한다. 릴레이가 이어 받는다.
                logger.warn(e) { "point outbox after-commit step failed (relay will retry)" }
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safe()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = safe()
            },
        )
    }
}
