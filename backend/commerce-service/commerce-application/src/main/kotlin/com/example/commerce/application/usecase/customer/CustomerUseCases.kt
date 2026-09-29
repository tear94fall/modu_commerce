package com.example.commerce.application.usecase.customer

import com.example.commerce.application.domain.entity.Customer
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.member.MemberLookup
import com.example.commerce.application.service.CustomerCommandService
import com.example.commerce.application.service.CustomerQueryService
import com.example.commerce.application.service.CustomerSummaryQueryService
import com.example.commerce.application.service.TierCommandService
import com.example.commerce.application.service.TierRunCommandService
import com.example.commerce.application.service.TierRunService
import com.example.commerce.application.service.above
import com.example.commerce.application.service.tierFor
import com.example.commerce.application.usecase.command.TierCommand
import com.example.commerce.application.usecase.result.AdminCustomerDetailResult
import com.example.commerce.application.usecase.result.AdminCustomerResult
import com.example.commerce.application.usecase.result.AdminTierResult
import com.example.commerce.application.usecase.result.CustomerCouponCountsResult
import com.example.commerce.application.usecase.result.CustomerLookupResult
import com.example.commerce.application.usecase.result.CustomerMeResult
import com.example.commerce.application.usecase.result.CustomerRecentOrderResult
import com.example.commerce.application.usecase.result.CustomerSummaryResult
import com.example.commerce.application.usecase.result.MonthlyCouponResult
import com.example.commerce.application.usecase.result.OrderCountsResult
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.PublicTierResult
import com.example.commerce.application.usecase.result.RollingResult
import com.example.commerce.application.usecase.result.TierHistoryResult
import com.example.commerce.application.usecase.result.TierRunResult
import com.example.commerce.application.usecase.result.TierSummaryResult
import com.example.commerce.application.usecase.result.summaryTier
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 앱: 내 고객 정보와 가입(약관 동의). */
@Component
class MyCustomerUseCase(
    private val customerQueryService: CustomerQueryService,
    private val customerCommandService: CustomerCommandService,
) {
    /**
     * 가입(동의)한 고객만. 아니면 CustomerRequiredException. 앱은 이 응답으로 가입 화면을 띄울지 정하므로
     * 고객 행은 master 에서 읽는다(가입 직후 다시 불러도 404 가 나지 않게). 등급표·누적 금액은 레플리카.
     */
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun get(userId: String): CustomerMeResult = me(customerCommandService.agreed(userId))

    /** 필수 약관 둘 다 동의해야 한다. */
    fun join(
        userId: String,
        agreeTerms: Boolean,
        agreePrivacy: Boolean,
        marketing: Boolean,
    ): CustomerMeResult {
        require(agreeTerms && agreePrivacy) { "필수 약관(이용약관, 개인정보 수집·이용)에 모두 동의해 주세요." }
        // 방금 쓴 고객 행으로 응답한다(레플리카에서 다시 읽으면 지연 동안 "가입 필요"가 나온다).
        return me(customerCommandService.join(userId, marketing))
    }

    private fun me(c: Customer): CustomerMeResult {
        val tiers = customerQueryService.tiers()
        val rolling = customerQueryService.rollingAmount(c.userId)
        val expected = tiers.tierFor(rolling)
        val next = tiers.above(expected)
        return CustomerMeResult(
            userId = c.userId,
            joinedAt = c.joinedAt,
            termsAgreedAt = c.termsAgreedAt,
            privacyAgreedAt = c.privacyAgreedAt,
            termsVersion = c.termsVersion,
            tier = TierSummaryResult.from(c.summaryTier(tiers)),
            basisAmount = c.tierBasisAmount,
            rolling =
                RollingResult(
                    amount = rolling,
                    expectedTier = TierSummaryResult.from(expected),
                    nextTier = next?.let(TierSummaryResult::from),
                    amountToNext = next?.let { it.minAmount - rolling },
                ),
            periodLabel = customerQueryService.periodLabel(),
        )
    }
}

/** 앱: 등급 안내(공개). */
@Component
class PublicTiersUseCase(
    private val customerQueryService: CustomerQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(): List<PublicTierResult> {
        val tiers = customerQueryService.tiers()
        val coupons = customerQueryService.monthlyCoupons(tiers)
        return tiers.map { t ->
            PublicTierResult(TierSummaryResult.from(t), coupons[t.code].orEmpty().map(MonthlyCouponResult::from))
        }
    }
}

/** 백오피스: 등급 설정과 산정 실행. */
@Component
class AdminTierUseCase(
    private val customerQueryService: CustomerQueryService,
    private val tierCommandService: TierCommandService,
    private val tierRunService: TierRunService,
    private val tierRunCommandService: TierRunCommandService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun list(): List<AdminTierResult> = results(customerQueryService.tiers())

    /** 방금 저장한 등급(master)으로 응답한다. 레플리카에서 다시 읽으면 지연 동안 옛 설정이 보인다. */
    fun update(commands: List<TierCommand>): List<AdminTierResult> = results(tierCommandService.update(commands))

    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun runs(
        page: Int,
        size: Int,
    ): PageResult<TierRunResult> = PageResult.from(customerQueryService.runs(page, size), TierRunResult::from)

    /** 수동 산정을 시작하고 그 실행 행(지금 상태)을 돌려준다. */
    fun startRun(): TierRunResult {
        val id = tierRunService.startManual()
        return TierRunResult.from(tierRunCommandService.find(id))
    }

    private fun results(tiers: List<Tier>): List<AdminTierResult> {
        val coupons = customerQueryService.monthlyCoupons(tiers)
        val counts = customerQueryService.activeCounts()
        return tiers.map { AdminTierResult.from(it, coupons[it.code].orEmpty(), counts[it.code] ?: 0) }
    }
}

/** 백오피스: 고객 목록·상세·회원 화면 배지 조회. 이름·이메일은 member-service 에서 페이지 단위로 받는다. */
@Component
class AdminCustomerUseCase(
    private val customerQueryService: CustomerQueryService,
    private val customerSummaryQueryService: CustomerSummaryQueryService,
    private val memberLookup: MemberLookup,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun search(
        q: String?,
        tier: String?,
        agreed: Boolean?,
        page: Int,
        size: Int,
    ): PageResult<AdminCustomerResult> {
        val result = customerQueryService.adminPage(q, tier, agreed, page, size)
        val rows = results(result.content).associateBy { it.userId }
        return PageResult.from(result) { rows.getValue(it.userId) }
    }

    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun lookup(userIds: List<String>): List<CustomerLookupResult> {
        val ids = userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(ids.size <= 100) { "한 번에 100명까지 조회할 수 있습니다." }
        val tiers = customerQueryService.tiers()
        val byId = customerQueryService.findAll(ids).associateBy { it.userId }
        return ids.mapNotNull { byId[it] }.map { c ->
            CustomerLookupResult(c.userId, c.status, TierSummaryResult.from(c.summaryTier(tiers)), c.isAgreed())
        }
    }

    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun detail(userId: String): AdminCustomerDetailResult {
        val customer = customerQueryService.find(userId) ?: throw EntityNotFoundException("$userId 에 해당하는 고객이 없습니다.")
        return AdminCustomerDetailResult(
            results(listOf(customer)).single(),
            customerQueryService.histories(userId).map(TierHistoryResult::from),
        )
    }

    /** 회원 허브 요약. 고객이 아니거나 없는 회원이어도 0·null 로 채운다. */
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun summary(userId: String): CustomerSummaryResult {
        val customer = customerQueryService.find(userId)?.let { results(listOf(it)).single() }
        val stats = customerSummaryQueryService.orderStats(userId).associateBy { it.status }
        val coupons = customerSummaryQueryService.couponCounts(userId)
        return CustomerSummaryResult(
            customer = customer,
            orderCounts =
                OrderCountsResult(
                    paid = stats[OrderStatus.PAID]?.count ?: 0,
                    shipping = stats[OrderStatus.SHIPPING]?.count ?: 0,
                    delivered = stats[OrderStatus.DELIVERED]?.count ?: 0,
                    cancelled = stats[OrderStatus.CANCELLED]?.count ?: 0,
                ),
            deliveredAmountTotal = stats[OrderStatus.DELIVERED]?.paymentAmount ?: 0,
            lastOrderAt = stats.values.mapNotNull { it.lastCreatedAt }.maxOrNull(),
            recentOrders = customerSummaryQueryService.recentOrders(userId, RECENT_ORDERS).map(CustomerRecentOrderResult::from),
            coupons = CustomerCouponCountsResult(coupons.available ?: 0, coupons.used ?: 0, coupons.expired ?: 0),
            wishlistCount = customerSummaryQueryService.wishlistCount(userId),
            reviewCount = customerSummaryQueryService.reviewCount(userId),
        )
    }

    private fun results(customers: List<Customer>): List<AdminCustomerResult> {
        if (customers.isEmpty()) return emptyList()
        val ids = customers.map { it.userId }
        val tiers = customerQueryService.tiers()
        val rolling = customerQueryService.rollingAmounts(ids)
        val members = memberLookup.findAll(ids)
        return customers.map { c ->
            AdminCustomerResult(
                userId = c.userId,
                name = members[c.userId]?.username,
                email = members[c.userId]?.email,
                status = c.status,
                tier = TierSummaryResult.from(c.summaryTier(tiers)),
                basisAmount = c.tierBasisAmount,
                rollingAmount = rolling[c.userId] ?: 0L,
                joinedAt = c.joinedAt,
                termsAgreedAt = c.termsAgreedAt,
                privacyAgreedAt = c.privacyAgreedAt,
                migrated = c.migrated,
            )
        }
    }
}

private const val RECENT_ORDERS = 5

/** 내부: 모두 계정 탈퇴. */
@Component
class WithdrawCustomerUseCase(
    private val customerCommandService: CustomerCommandService,
) {
    fun execute(userId: String) = customerCommandService.withdraw(userId)
}
