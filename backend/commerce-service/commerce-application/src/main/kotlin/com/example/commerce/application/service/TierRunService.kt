package com.example.commerce.application.service

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.CustomerStatus
import com.example.commerce.application.domain.entity.TierChangeReason
import com.example.commerce.application.domain.entity.TierHistory
import com.example.commerce.application.domain.entity.TierRun
import com.example.commerce.application.domain.entity.TierRunReason
import com.example.commerce.application.domain.entity.TierRunStatus
import com.example.commerce.application.domain.entity.UserCoupon
import com.example.commerce.application.domain.repository.rw.CustomerRwRepository
import com.example.commerce.application.domain.repository.rw.OrderRwRepository
import com.example.commerce.application.domain.repository.rw.TierHistoryRwRepository
import com.example.commerce.application.domain.repository.rw.TierRunRwRepository
import com.example.commerce.application.domain.repository.rw.TierRwRepository
import com.example.commerce.application.push.PushAsyncRunner
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneOffset

/** 산정 결과(쿠폰 발급 전). */
data class TierRecalcOutcome(
    val month: YearMonth,
    /** 쿠폰 id → 받을 고객(가입·동의한 그 등급 고객). */
    val couponTargets: Map<Long, List<String>>,
)

@Service
@Transactional(transactionManager = "rwTransactionManager")
class TierRunCommandService(
    private val tierRunRwRepository: TierRunRwRepository,
    private val tierRwRepository: TierRwRepository,
    private val tierHistoryRwRepository: TierHistoryRwRepository,
    private val customerRwRepository: CustomerRwRepository,
    private val orderRwRepository: OrderRwRepository,
    private val clock: Clock,
) {
    /** RUNNING 인 실행이 있으면 [TierRunConflictException]. */
    fun create(reason: TierRunReason): TierRun {
        if (tierRunRwRepository.existsByStatus(TierRunStatus.RUNNING)) throw TierRunConflictException()
        val month = TierPeriods.currentMonth(clock)
        return tierRunRwRepository.saveAndFlush(TierRun(TierPeriods.basisLabel(month), reason, TierPeriods.utcNow(clock)))
    }

    /**
     * 가입 상태(ACTIVE — 동의했거나 옮겨 온) 고객 모두의 등급을 기준 기간 금액으로 다시 정하고, 바뀐 사람은 이력을 남긴다.
     * 매월 쿠폰을 받을 대상(ACTIVE + 동의)을 돌려준다.
     */
    fun recalculate(runId: Long): TierRecalcOutcome {
        val run = tierRunRwRepository.findByIdOrNull(runId) ?: error("tier run $runId not found")
        val month = YearMonth.from(run.startedAt.atOffset(ZoneOffset.UTC).atZoneSameInstant(TierPeriods.KST))
        val tiers = tierRwRepository.findAllByOrderBySortOrderAsc()
        val sums =
            orderRwRepository
                .sumDelivered(TierPeriods.basisFrom(month), TierPeriods.basisTo(month))
                .associate { it.userId to (it.amount ?: 0L) }
        val now = TierPeriods.utcNow(clock)
        val reason = if (run.reason == TierRunReason.MONTHLY) TierChangeReason.MONTHLY else TierChangeReason.MANUAL
        val customers = customerRwRepository.findAllByStatus(CustomerStatus.ACTIVE)
        var changed = 0
        customers.forEach { c ->
            val basis = sums[c.userId] ?: 0L
            val from = c.tierCode
            val tier = tiers.tierFor(basis)
            if (c.applyTier(tier.code, basis, now)) {
                changed++
                tierHistoryRwRepository.save(TierHistory(c.userId, from, tier.code, basis, run.periodLabel, reason, now, runId))
            }
        }
        val counts = tiers.associate { t -> t.code to customers.count { it.tierCode == t.code } }
        run.recalculated(customers.size, changed, counts.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" })
        val agreedByTier = customers.filter { it.isAgreed() }.groupBy({ it.tierCode }, { it.userId })
        val targets = mutableMapOf<Long, MutableList<String>>()
        tiers.forEach { t ->
            val users = agreedByTier[t.code].orEmpty()
            if (users.isEmpty()) return@forEach
            t.couponIds.distinct().forEach { id -> targets.getOrPut(id) { mutableListOf() }.addAll(users) }
        }
        logger.info { "tier run $runId (${run.reason}) ${run.periodLabel}: customers=${customers.size} changed=$changed $counts" }
        return TierRecalcOutcome(month, targets)
    }

    fun finish(
        runId: Long,
        issued: Int,
        skipped: Int,
    ) {
        tierRunRwRepository.findByIdOrNull(runId)?.done(issued, skipped, TierPeriods.utcNow(clock))
    }

    fun fail(
        runId: Long,
        message: String,
    ) {
        tierRunRwRepository.findByIdOrNull(runId)?.failed(message, TierPeriods.utcNow(clock))
    }
}

/**
 * 등급 산정 실행. 매월 1일(스케줄러) 또는 관리자 수동. 단계마다 짧은 트랜잭션을 쓴다.
 * 쿠폰은 발급 키 `tier:<그 달>` 로 주므로 같은 달에 다시 돌려도 이미 받은 사람에게는 또 주지 않는다.
 */
@Service
class TierRunService(
    private val tierRunCommandService: TierRunCommandService,
    private val couponIssueService: CouponIssueService,
    private val asyncRunner: PushAsyncRunner,
) {
    /** 관리자 "지금 다시 산정". 실행 행을 만들고(이미 돌고 있으면 409) 요청 스레드 밖에서 돌린다. */
    fun startManual(): Long {
        val run = tierRunCommandService.create(TierRunReason.MANUAL)
        val id = requireNotNull(run.id)
        asyncRunner.run { execute(id) }
        return id
    }

    /** 매월 1일 스케줄러. 다른 실행이 돌고 있으면 건너뛴다. */
    fun runMonthly(): Long? {
        val run =
            try {
                tierRunCommandService.create(TierRunReason.MONTHLY)
            } catch (e: TierRunConflictException) {
                logger.warn { "monthly tier run skipped: another run is RUNNING" }
                return null
            }
        val id = requireNotNull(run.id)
        execute(id)
        return id
    }

    fun execute(runId: Long) {
        try {
            val outcome = tierRunCommandService.recalculate(runId)
            val key = UserCoupon.tierKey(TierPeriods.key(outcome.month))
            var issued = 0
            var skipped = 0
            outcome.couponTargets.forEach { (couponId, userIds) ->
                val result = couponIssueService.issueTier(couponId, userIds, key)
                issued += result.issued
                skipped += result.skipped
            }
            tierRunCommandService.finish(runId, issued, skipped)
            logger.info { "tier run $runId done: coupons issued=$issued skipped=$skipped ($key)" }
        } catch (e: Exception) {
            logger.error(e) { "tier run $runId failed" }
            tierRunCommandService.fail(runId, e.message ?: e.javaClass.simpleName)
        }
    }
}
