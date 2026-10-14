package com.example.commerce.application.usecase.coupon

import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.EventKind
import com.example.commerce.application.domain.entity.PromotionStatus
import com.example.commerce.application.domain.entity.UserCoupon
import com.example.commerce.application.domain.entity.UserCouponStatus
import com.example.commerce.application.service.CouponAlreadyIssuedException
import com.example.commerce.application.service.CouponCommandService
import com.example.commerce.application.service.CouponIssueService
import com.example.commerce.application.service.CouponQueryService
import com.example.commerce.application.service.CouponUseService
import com.example.commerce.application.service.PromotionCommandService
import com.example.commerce.application.service.PromotionQueryService
import com.example.commerce.application.usecase.command.CouponCommand
import com.example.commerce.application.usecase.command.OrderLineCommand
import com.example.commerce.application.usecase.result.AdminCouponDetailResult
import com.example.commerce.application.usecase.result.AdminCouponIssueResult
import com.example.commerce.application.usecase.result.AdminCouponSummaryResult
import com.example.commerce.application.usecase.result.CouponClaimResult
import com.example.commerce.application.usecase.result.CouponGrantResult
import com.example.commerce.application.usecase.result.CouponOfferResult
import com.example.commerce.application.usecase.result.MyCouponResult
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.ScopeTargetResult
import com.example.commerce.application.usecase.result.SkippedUserResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 받을 수 있는 쿠폰 카드들. 내가 이미 받은 것은 downloaded. */
fun CouponQueryService.offers(
    userId: String,
    coupons: List<Coupon>,
): List<CouponOfferResult> {
    val owned = ownedIds(userId, coupons.mapNotNull { it.id })
    return coupons.map { CouponOfferResult.from(it, scopeLabel(it), it.id in owned) }
}

fun CouponQueryService.adminSummaries(coupons: List<Coupon>): List<AdminCouponSummaryResult> {
    val used = usedCounts(coupons.mapNotNull { it.id })
    return coupons.map { AdminCouponSummaryResult.from(it, scopeLabel(it), used[it.id] ?: 0) }
}

fun CouponQueryService.mine(uc: UserCoupon): MyCouponResult = MyCouponResult.from(uc, today(), scopeLabel(uc.coupon))

@Component
class MyCouponsUseCase(
    private val couponQueryService: CouponQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun list(
        userId: String,
        status: UserCouponStatus,
    ): List<MyCouponResult> {
        val today = couponQueryService.today()
        val mine = couponQueryService.mine(userId).filter { it.statusOn(today) == status }
        val sorted =
            if (status == UserCouponStatus.AVAILABLE) {
                mine.sortedWith(compareBy<UserCoupon> { it.expiresOn }.thenByDescending { it.id })
            } else {
                mine.sortedByDescending { it.id }
            }
        return sorted.map { couponQueryService.mine(it) }
    }

    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun availableCount(userId: String): Int {
        val today = couponQueryService.today()
        return couponQueryService.mine(userId).count { it.statusOn(today) == UserCouponStatus.AVAILABLE }
    }

    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun downloadable(
        userId: String,
        productId: Long?,
    ): List<CouponOfferResult> = couponQueryService.offers(userId, couponQueryService.downloadable(productId))
}

@Component
class IssueCouponUseCase(
    private val couponIssueService: CouponIssueService,
    private val couponQueryService: CouponQueryService,
    private val promotionQueryService: PromotionQueryService,
    private val promotionCommandService: PromotionCommandService,
) {
    fun download(
        userId: String,
        couponId: Long,
    ): MyCouponResult = couponQueryService.mine(couponIssueService.download(userId, couponId))

    fun redeem(
        userId: String,
        code: String,
    ): MyCouponResult = couponQueryService.mine(couponIssueService.redeem(userId, code))

    /**
     * 쿠폰 이벤트: 아직 없는 이벤트 쿠폰을 모두 준다. 쿠폰마다 따로 발급(따로 트랜잭션)하니 한 장이 소진돼도 나머지는 받는다.
     * 받은 게 없고 전부 이미 있으면 409, 다른 이유로 하나도 못 받으면 그 이유로 400.
     */
    fun claimEvent(
        userId: String,
        promotionId: Long,
    ): CouponClaimResult {
        // 발급 전에 보는 조건(노출·진행 중·쿠폰 목록)이라 master 에서 읽는다(방금 숨기거나 끝낸 이벤트로 발급하지 않게).
        val promotion = promotionCommandService.visible(promotionId)
        require(promotion.kind() == EventKind.COUPON) { "쿠폰 이벤트가 아닙니다." }
        require(promotion.statusOn(promotionQueryService.today()) == PromotionStatus.ONGOING) { "진행 중인 이벤트가 아닙니다." }
        val issued = mutableListOf<MyCouponResult>()
        var alreadyHad = 0
        var firstReason: String? = null
        promotion.couponIds.forEach { couponId ->
            try {
                issued += couponQueryService.mine(couponIssueService.issue(userId, couponId, CouponSource.EVENT))
            } catch (e: CouponAlreadyIssuedException) {
                alreadyHad++
            } catch (e: IllegalArgumentException) {
                firstReason = firstReason ?: e.message
            } catch (e: jakarta.persistence.EntityNotFoundException) {
                firstReason = firstReason ?: "지금은 받을 수 없는 쿠폰입니다."
            }
        }
        if (issued.isEmpty()) {
            if (alreadyHad > 0 && firstReason == null) throw CouponAlreadyIssuedException()
            throw IllegalArgumentException(firstReason ?: "받을 수 있는 쿠폰이 없습니다.")
        }
        return CouponClaimResult(issued, alreadyHad)
    }
}

@Component
class ApplicableCouponsUseCase(
    private val couponUseService: CouponUseService,
    private val couponQueryService: CouponQueryService,
) {
    fun execute(
        userId: String,
        items: List<OrderLineCommand>,
    ): List<MyCouponResult> {
        require(items.isNotEmpty()) { "주문할 상품이 없습니다." }
        return couponUseService
            .applicable(userId, items)
            .map { (uc, calc) ->
                couponQueryService.mine(uc).copy(discount = calc.first, applicable = calc.first > 0, reason = calc.second)
            }.sortedByDescending { it.applicable }
    }
}

@Component
class AdminCouponUseCase(
    private val couponQueryService: CouponQueryService,
    private val couponCommandService: CouponCommandService,
    private val couponIssueService: CouponIssueService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun search(
        q: String?,
        active: Boolean?,
        page: Int,
        size: Int,
    ): PageResult<AdminCouponSummaryResult> {
        val result = couponQueryService.adminPage(q, active, page, size)
        val summaries = couponQueryService.adminSummaries(result.content).associateBy { it.id }
        return PageResult.from(result) { summaries.getValue(requireNotNull(it.id)) }
    }

    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun detail(id: Long): AdminCouponDetailResult = detailOf(couponQueryService.find(id))

    /** 방금 저장한 쿠폰(master 엔티티)으로 응답한다. 레플리카에서 다시 읽으면 지연 동안 404·옛값이 나온다. */
    fun create(command: CouponCommand): AdminCouponDetailResult = detailOf(couponCommandService.create(command))

    fun update(
        id: Long,
        command: CouponCommand,
    ): AdminCouponDetailResult = detailOf(couponCommandService.update(id, command))

    /** 쿠폰 자체는 넘겨받은 엔티티, 사용 수·적용 대상 이름(쓰기와 상관없는 값)은 레플리카에서. */
    private fun detailOf(coupon: Coupon): AdminCouponDetailResult {
        val summary = couponQueryService.adminSummaries(listOf(coupon)).single()
        val targets = couponQueryService.scopeTargets(coupon).map { ScopeTargetResult(it.first, it.second) }
        return AdminCouponDetailResult(summary, coupon.description, coupon.scopeIds.toList(), targets)
    }

    fun delete(id: Long) = couponCommandService.delete(id)

    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun issues(
        id: Long,
        status: UserCouponStatus?,
        page: Int,
        size: Int,
    ): PageResult<AdminCouponIssueResult> {
        val today = couponQueryService.today()
        return PageResult.from(couponQueryService.issues(id, status, page, size)) { AdminCouponIssueResult.from(it, today) }
    }

    /** 회원에게 지급. 사람마다 따로 발급해 한 명이 실패해도 나머지는 받는다. */
    fun grant(
        id: Long,
        userIds: List<String>,
    ): CouponGrantResult {
        val ids = userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(ids.size in 1..100) { "지급할 회원을 1~100명 고르세요." }
        // 쿠폰이 없으면 첫 발급(master 에서 쿠폰 행을 잠근다)이 404 를 던진다. 레플리카로 미리 보지 않는다(방금 만든 쿠폰이 없다고 나올 수 있다).
        var issued = 0
        val skipped = mutableListOf<SkippedUserResult>()
        ids.forEach { userId ->
            try {
                couponIssueService.issue(userId, id, CouponSource.ADMIN)
                issued++
            } catch (e: CouponAlreadyIssuedException) {
                skipped += SkippedUserResult(userId, e.message ?: "이미 받은 쿠폰입니다.")
            } catch (e: IllegalArgumentException) {
                skipped += SkippedUserResult(userId, e.message ?: "지급할 수 없습니다.")
            }
        }
        return CouponGrantResult(issued, skipped)
    }
}
