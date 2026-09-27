package com.example.commerce.application.service

import com.example.commerce.application.common.TierPeriods
import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.Customer
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.domain.entity.TierChangeReason
import com.example.commerce.application.domain.entity.TierHistory
import com.example.commerce.application.domain.entity.TierRun
import com.example.commerce.application.domain.repository.ro.CouponRoRepository
import com.example.commerce.application.domain.repository.ro.CustomerRoRepository
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.TierHistoryRoRepository
import com.example.commerce.application.domain.repository.ro.TierRoRepository
import com.example.commerce.application.domain.repository.ro.TierRunRoRepository
import com.example.commerce.application.domain.repository.rw.CouponRwRepository
import com.example.commerce.application.domain.repository.rw.CustomerRwRepository
import com.example.commerce.application.domain.repository.rw.OrderRwRepository
import com.example.commerce.application.domain.repository.rw.PushConsentRwRepository
import com.example.commerce.application.domain.repository.rw.PushDeviceRwRepository
import com.example.commerce.application.domain.repository.rw.TierHistoryRwRepository
import com.example.commerce.application.domain.repository.rw.TierRwRepository
import com.example.commerce.application.usecase.command.TierCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 커머스 가입(약관 동의)이 필요하다(403, 앱의 GET /me/customer 는 404). */
class CustomerRequiredException : RuntimeException(MESSAGE) {
    companion object {
        const val MESSAGE = "모두의 커머스 가입이 필요합니다"
        const val CODE = "CUSTOMER_REQUIRED"
    }
}

/** 등급 산정이 이미 돌고 있다(409). */
class TierRunConflictException : RuntimeException("이미 등급 산정이 진행 중입니다.")

/** 기준 금액 이상인 가장 높은 등급. 등급은 기준 금액 오름차순이 아니어도 된다. */
fun List<Tier>.tierFor(amount: Long): Tier {
    val sorted = sortedBy { it.minAmount }
    return sorted.lastOrNull { it.minAmount <= amount } ?: sorted.first()
}

/** 한 단계 위 등급(없으면 null). */
fun List<Tier>.above(tier: Tier): Tier? = sortedBy { it.minAmount }.firstOrNull { it.minAmount > tier.minAmount }

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class CustomerQueryService(
    private val customerRoRepository: CustomerRoRepository,
    private val tierRoRepository: TierRoRepository,
    private val tierHistoryRoRepository: TierHistoryRoRepository,
    private val tierRunRoRepository: TierRunRoRepository,
    private val orderRoRepository: OrderRoRepository,
    private val couponRoRepository: CouponRoRepository,
    private val clock: Clock,
) {
    fun tiers(): List<Tier> = tierRoRepository.findAllByOrderBySortOrderAsc()

    fun find(userId: String): Customer? = customerRoRepository.findByUserId(userId)

    /** 가입 상태인 고객의 지금 등급(주문 적립 예정). 고객이 아니면 null. */
    fun currentTier(userId: String): Tier? {
        val customer = find(userId)?.takeIf { it.isActive() } ?: return null
        val tiers = tiers()
        return tiers.firstOrNull { it.code == customer.tierCode } ?: tiers.minByOrNull { it.minAmount }
    }

    fun isAgreed(userId: String): Boolean = find(userId)?.isAgreed() ?: false

    /** 가입(동의)한 고객. 아니면 [CustomerRequiredException]. */
    fun agreed(userId: String): Customer = find(userId)?.takeIf { it.isAgreed() } ?: throw CustomerRequiredException()

    fun findAll(userIds: Collection<String>): List<Customer> =
        if (userIds.isEmpty()) emptyList() else customerRoRepository.findAllByUserIdIn(userIds)

    /** 다음 산정에 쓰일 기간(5개월 전 1일 ~ 지금)의 배송 완료 결제 금액. */
    fun rollingAmounts(userIds: Collection<String>): Map<String, Long> {
        if (userIds.isEmpty()) return emptyMap()
        val month = TierPeriods.currentMonth(clock)
        return orderRoRepository
            .sumDelivered(userIds, TierPeriods.rollingFrom(month), TierPeriods.rollingTo(month))
            .associate { it.userId to (it.amount ?: 0L) }
    }

    fun rollingAmount(userId: String): Long = rollingAmounts(listOf(userId))[userId] ?: 0L

    /** 지금 등급의 기준 기간 이름(이번 달 산정 기준). */
    fun periodLabel(): String = TierPeriods.basisLabel(TierPeriods.currentMonth(clock))

    fun adminPage(
        q: String?,
        tier: String?,
        agreed: Boolean?,
        page: Int,
        size: Int,
    ): Page<Customer> =
        customerRoRepository.searchAdmin(
            q?.trim()?.takeIf { it.isNotEmpty() },
            tier?.trim()?.takeIf { it.isNotEmpty() },
            agreed,
            PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)),
        )

    fun histories(userId: String): List<TierHistory> = tierHistoryRoRepository.findTop24ByUserIdOrderByChangedAtDescIdDesc(userId)

    fun activeCounts(): Map<String, Long> = customerRoRepository.countActiveByTier().associate { it.tierCode to it.count }

    fun runs(
        page: Int,
        size: Int,
    ): Page<TierRun> = tierRunRoRepository.findAllByOrderByIdDesc(PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)))

    fun run(id: Long): TierRun? = tierRunRoRepository.findById(id)

    /** 등급마다 매월 쿠폰(지운 쿠폰은 뺀다). */
    fun monthlyCoupons(tiers: List<Tier>): Map<String, List<Coupon>> {
        val ids = tiers.flatMap { it.couponIds }.toSet()
        val byId = if (ids.isEmpty()) emptyMap() else couponRoRepository.findLiveByIds(ids).associateBy { it.id }
        return tiers.associate { t -> t.code to t.couponIds.mapNotNull { byId[it] } }
    }
}

/** 가입·탈퇴와 가입 때 등급 산정. */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class CustomerCommandService(
    private val customerRwRepository: CustomerRwRepository,
    private val tierRwRepository: TierRwRepository,
    private val tierHistoryRwRepository: TierHistoryRwRepository,
    private val orderRwRepository: OrderRwRepository,
    private val pushDeviceRwRepository: PushDeviceRwRepository,
    private val pushConsentRwRepository: PushConsentRwRepository,
    private val pushConsentCommandService: PushConsentCommandService,
    @Value("\${modu.customer.terms-version:2026-10}") private val termsVersion: String,
    private val clock: Clock,
) {
    /**
     * 약관 동의로 가입한다. 옮겨 온 행·탈퇴한 행이 있으면 동의와 상태만 바꾸고 나머지는 그대로 둔다.
     * 등급은 이번 달 기준 기간(지난 6개월)으로 바로 정한다(바뀌면 JOIN 이력). [marketing] 이 true 면 혜택 알림에 동의한다.
     */
    fun join(
        userId: String,
        marketing: Boolean,
    ): Customer {
        val now = TierPeriods.utcNow(clock)
        val tiers = tierRwRepository.findAllByOrderBySortOrderAsc()
        require(tiers.isNotEmpty()) { "회원 등급이 없습니다." }
        val existing = customerRwRepository.findForUpdate(userId)
        val customer = existing ?: Customer(userId, now, tiers.tierFor(0).code)
        customer.agree(termsVersion, now)
        val month = TierPeriods.currentMonth(clock)
        val basis =
            orderRwRepository
                .sumDeliveredOf(listOf(userId), TierPeriods.basisFrom(month), TierPeriods.basisTo(month))
                .firstOrNull()
                ?.amount ?: 0L
        val tier = tiers.tierFor(basis)
        val from = if (existing == null) null else customer.tierCode
        val changed = customer.applyTier(tier.code, basis, now)
        if (existing == null || changed) {
            tierHistoryRwRepository.save(
                TierHistory(userId, from, tier.code, basis, TierPeriods.basisLabel(month), TierChangeReason.JOIN, now),
            )
        }
        val saved = customerRwRepository.saveAndFlush(customer)
        if (marketing) pushConsentCommandService.change(userId, marketing = true, night = false)
        logger.info { "commerce customer $userId joined (tier ${tier.code}, basis $basis, migrated=${customer.migrated})" }
        return saved
    }

    /** 모두 계정 탈퇴. 고객은 WITHDRAWN 으로 두고(주문 등은 남는다) 푸시 기기·동의를 지운다. 없는 고객이어도 그만. */
    fun withdraw(userId: String) {
        customerRwRepository.findByIdOrNull(userId)?.withdraw(TierPeriods.utcNow(clock))
        val devices = pushDeviceRwRepository.deleteAllOfUser(userId)
        pushConsentRwRepository.deleteOfUser(userId)
        logger.info { "commerce customer $userId withdrawn (push devices removed: $devices)" }
    }
}

/** 백오피스 등급 설정. 코드 4개는 고정이고 전체 목록으로 바꾼다. */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class TierCommandService(
    private val tierRwRepository: TierRwRepository,
    private val couponRwRepository: CouponRwRepository,
) {
    fun update(commands: List<TierCommand>): List<Tier> {
        val tiers = tierRwRepository.findAllByOrderBySortOrderAsc()
        val codes = commands.map { it.code.trim() }
        require(codes.size == codes.toSet().size && codes.toSet() == tiers.map { it.code }.toSet()) {
            "등급 코드는 ${tiers.joinToString(", ") { it.code }} 를 모두 한 번씩 보내야 합니다."
        }
        // 순서는 지금 등급 순서를 따른다(보낸 순서와 상관없이).
        val byCode = commands.associateBy { it.code.trim() }
        val ordered = tiers.map { byCode.getValue(it.code) }
        ordered.forEach { it.validate() }
        require(ordered.first().minAmount == 0L) { "가장 낮은 등급(${ordered.first().code})의 기준 금액은 0원이어야 합니다." }
        ordered.zipWithNext().forEach { (a, b) ->
            require(b.minAmount > a.minAmount) { "${b.code} 의 기준 금액은 ${a.code} 보다 커야 합니다." }
        }
        ordered.flatMap { it.couponIds }.distinct().forEach { id ->
            require(couponRwRepository.findLive(id) != null) { "없는 쿠폰입니다: $id" }
        }
        tiers.forEachIndexed { i, tier ->
            val c = ordered[i]
            tier.update(c.name.trim(), c.color.uppercase(), c.minAmount, c.earnRate, i, c.couponIds.distinct())
        }
        return tierRwRepository.saveAllAndFlush(tiers)
    }
}
