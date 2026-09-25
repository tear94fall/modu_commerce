package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.Coupon
import com.example.commerce.application.domain.entity.CouponLine
import com.example.commerce.application.domain.entity.CouponScope
import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.UserCoupon
import com.example.commerce.application.domain.entity.UserCouponStatus
import com.example.commerce.application.domain.repository.ro.CategoryRoRepository
import com.example.commerce.application.domain.repository.ro.CouponRoRepository
import com.example.commerce.application.domain.repository.ro.ProductRoRepository
import com.example.commerce.application.domain.repository.ro.UserCouponRoRepository
import com.example.commerce.application.domain.repository.rw.CouponRwRepository
import com.example.commerce.application.domain.repository.rw.ProductSkuRwRepository
import com.example.commerce.application.domain.repository.rw.UserCouponRwRepository
import com.example.commerce.application.usecase.command.CouponCommand
import com.example.commerce.application.usecase.command.OrderLineCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 이미 받은 쿠폰(409). */
class CouponAlreadyIssuedException : RuntimeException("이미 받은 쿠폰입니다.")

/** 코드가 없거나 쓸 수 없는 쿠폰(404). */
class CouponCodeNotFoundException : RuntimeException("쿠폰 코드를 확인해 주세요.")

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class CouponQueryService(
    private val couponRoRepository: CouponRoRepository,
    private val userCouponRoRepository: UserCouponRoRepository,
    private val categoryRoRepository: CategoryRoRepository,
    private val productRoRepository: ProductRoRepository,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun downloadable(productId: Long?): List<Coupon> {
        val coupons = couponRoRepository.findDownloadable(today())
        if (productId == null) return coupons
        val product = productRoRepository.findById(productId) ?: return emptyList()
        return coupons.filter { it.covers(product) }
    }

    fun live(ids: Collection<Long>): List<Coupon> {
        if (ids.isEmpty()) return emptyList()
        val byId = couponRoRepository.findLiveByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun ownedIds(
        userId: String,
        couponIds: Collection<Long>,
    ): Set<Long> = if (couponIds.isEmpty()) emptySet() else userCouponRoRepository.findOwnedCouponIds(userId, couponIds).toSet()

    fun mine(userId: String): List<UserCoupon> = userCouponRoRepository.findAllByUser(userId)

    fun adminPage(
        q: String?,
        active: Boolean?,
        page: Int,
        size: Int,
    ): Page<Coupon> =
        couponRoRepository.searchAdmin(q?.trim()?.takeIf { it.isNotEmpty() }, active, PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)))

    fun find(id: Long): Coupon = couponRoRepository.findLive(id) ?: throw notFound(id)

    fun usedCounts(ids: Collection<Long>): Map<Long, Long> =
        if (ids.isEmpty()) emptyMap() else userCouponRoRepository.countUsed(ids).associate { it.couponId to it.count }

    fun issues(
        couponId: Long,
        status: UserCouponStatus?,
        page: Int,
        size: Int,
    ): Page<UserCoupon> {
        find(couponId)
        return userCouponRoRepository.findIssues(couponId, status?.name, today(), PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)))
    }

    /** "전체 상품" / "'문구' 카테고리" / "'문구' 외 2개 카테고리" / "'노트' 전용" / "지정 상품 3개". */
    fun scopeLabel(coupon: Coupon): String {
        val ids = coupon.scopeIds.toList()
        return when (coupon.scope) {
            CouponScope.ALL -> "전체 상품"
            CouponScope.CATEGORY -> {
                val names = ids.mapNotNull { categoryRoRepository.findById(it)?.name }
                when {
                    names.isEmpty() -> "지정 카테고리"
                    names.size == 1 -> "'${names[0]}' 카테고리"
                    else -> "'${names[0]}' 외 ${names.size - 1}개 카테고리"
                }
            }
            CouponScope.PRODUCT ->
                if (ids.size == 1) {
                    productRoRepository.findById(ids[0])?.let { "'${it.name}' 전용" } ?: "지정 상품 1개"
                } else {
                    "지정 상품 ${ids.size}개"
                }
        }
    }

    /** 카테고리·상품 이름(백오피스 상세). */
    fun scopeTargets(coupon: Coupon): List<Pair<Long, String>> =
        when (coupon.scope) {
            CouponScope.ALL -> emptyList()
            CouponScope.CATEGORY -> coupon.scopeIds.mapNotNull { id -> categoryRoRepository.findById(id)?.let { id to it.name } }
            CouponScope.PRODUCT -> {
                val byId = productRoRepository.findAllByIdIn(coupon.scopeIds).associateBy { it.id }
                coupon.scopeIds.map { id -> id to (byId[id]?.name ?: "삭제된 상품 #$id") }
            }
        }

    companion object {
        private val DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd")

        fun expiryLabel(coupon: Coupon): String = coupon.validUntil?.let { "${it.format(DOT)}까지" } ?: "받은 날부터 ${coupon.validDays}일"

        fun notFound(id: Long) = EntityNotFoundException("id: $id 에 해당하는 쿠폰이 없습니다.")
    }
}

@Service
@Transactional(transactionManager = "rwTransactionManager")
class CouponCommandService(
    private val couponRwRepository: CouponRwRepository,
    private val clock: Clock,
) {
    fun create(command: CouponCommand): Coupon {
        command.validate()
        requireCodeFree(command.code, null)
        val coupon = Coupon(command.name, command.discountType, command.discountValue, command.issueStart, command.issueEnd)
        apply(coupon, command)
        return couponRwRepository.saveAndFlush(coupon)
    }

    fun update(
        id: Long,
        command: CouponCommand,
    ): Coupon {
        command.validate()
        val coupon = couponRwRepository.findLive(id) ?: throw CouponQueryService.notFound(id)
        requireCodeFree(command.code, id)
        apply(coupon, command)
        return couponRwRepository.saveAndFlush(coupon)
    }

    fun delete(id: Long) {
        val coupon = couponRwRepository.findLive(id) ?: throw CouponQueryService.notFound(id)
        coupon.delete(LocalDateTime.now(clock))
    }

    /** 지운 쿠폰의 코드도 유니크 인덱스에 남아 있으니 함께 본다. */
    private fun requireCodeFree(
        code: String?,
        selfId: Long?,
    ) {
        if (code == null) return
        val other = couponRwRepository.findAnyByCode(code)
        require(other == null || other.id == selfId) { "이미 쓰는 쿠폰 코드입니다: $code" }
    }

    private fun apply(
        coupon: Coupon,
        c: CouponCommand,
    ) = coupon.update(
        c.name,
        c.description,
        c.discountType,
        c.discountValue,
        c.maxDiscount,
        c.minOrderAmount,
        c.scope,
        c.scopeIds,
        c.issueStart,
        c.issueEnd,
        c.validUntil,
        c.validDays,
        c.totalQuantity,
        c.code,
        c.downloadable,
        c.active,
    )
}

/**
 * 쿠폰 발급. 쿠폰 행을 잠그고 수량을 센다(동시에 여러 명이 받아도 수량을 넘지 않는다). 한 사람은 쿠폰마다 한 장.
 * 관리자 지급은 받기 노출·발급 기간을 무시하고, 이벤트 발급은 받기 노출만 무시한다.
 */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class CouponIssueService(
    private val couponRwRepository: CouponRwRepository,
    private val userCouponRwRepository: UserCouponRwRepository,
    private val clock: Clock,
) {
    fun download(
        userId: String,
        couponId: Long,
    ): UserCoupon = issue(userId, couponId, CouponSource.DOWNLOAD)

    fun redeem(
        userId: String,
        code: String,
    ): UserCoupon {
        val normalized = code.trim().uppercase()
        val coupon = couponRwRepository.findLiveByCode(normalized)?.takeIf { it.active } ?: throw CouponCodeNotFoundException()
        return issue(userId, requireNotNull(coupon.id), CouponSource.CODE)
    }

    fun issue(
        userId: String,
        couponId: Long,
        source: CouponSource,
    ): UserCoupon {
        val coupon = couponRwRepository.findByIdForUpdate(couponId) ?: throw CouponQueryService.notFound(couponId)
        val today = LocalDate.now(clock)
        if (source != CouponSource.ADMIN) {
            require(coupon.active) { "지금은 받을 수 없는 쿠폰입니다." }
            require(coupon.inIssuePeriod(today)) { "쿠폰을 받을 수 있는 기간이 아닙니다." }
        }
        if (source == CouponSource.DOWNLOAD) require(coupon.downloadable) { "지금은 받을 수 없는 쿠폰입니다." }
        if (userCouponRwRepository.existsByCouponIdAndUserId(couponId, userId)) throw CouponAlreadyIssuedException()
        require(!coupon.soldOut()) { "쿠폰이 모두 소진되었습니다." }
        coupon.issued()
        return try {
            userCouponRwRepository.saveAndFlush(UserCoupon(coupon, userId, source, coupon.expiresOn(today)))
        } catch (e: DataIntegrityViolationException) {
            throw CouponAlreadyIssuedException()
        }
    }
}

/** 결제에 쓸 수 있는지, 얼마인지. 주문에 적용하고 취소하면 돌려준다. */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class CouponUseService(
    private val userCouponRwRepository: UserCouponRwRepository,
    private val productSkuRwRepository: ProductSkuRwRepository,
    private val clock: Clock,
) {
    /** 결제 화면: 내 쓸 수 있는 쿠폰마다 이 주문에서의 할인액. */
    @Transactional(transactionManager = "rwTransactionManager", readOnly = true)
    fun applicable(
        userId: String,
        items: List<OrderLineCommand>,
    ): List<Pair<UserCoupon, Pair<Long, String?>>> {
        val lines = linesOf(items)
        val today = LocalDate.now(clock)
        return userCouponRwRepository
            .findUnused(userId)
            .filter { it.statusOn(today) == UserCouponStatus.AVAILABLE }
            .map { it to it.coupon.discountFor(lines) }
            .sortedWith(compareByDescending<Pair<UserCoupon, Pair<Long, String?>>> { it.second.first }.thenBy { it.first.expiresOn })
    }

    /**
     * 주문에 쿠폰 할인을 건다. 주문에 상품을 다 담은 뒤, 포인트보다 먼저 부른다. 쿠폰 행은 잠근 채 돌려주고,
     * 주문을 저장한 다음 [markUsed] 로 사용 표시를 한다(주문 id 가 그때 생긴다).
     */
    fun applyTo(
        order: Order,
        userCouponId: Long,
    ): UserCoupon {
        val uc = userCouponRwRepository.findByIdForUpdate(userCouponId)?.takeIf { it.userId == order.userId }
        require(uc != null) { "쓸 수 없는 쿠폰입니다." }
        require(uc.statusOn(LocalDate.now(clock)) == UserCouponStatus.AVAILABLE) { "이미 사용했거나 기한이 지난 쿠폰입니다." }
        val (discount, reason) = uc.coupon.discountFor(order.items.map { CouponLine(it.product, it.lineAmount()) })
        require(discount > 0) { reason ?: "이 주문에는 쓸 수 없는 쿠폰입니다." }
        order.applyCoupon(requireNotNull(uc.id), uc.coupon.name, discount)
        return uc
    }

    fun markUsed(
        userCoupon: UserCoupon,
        order: Order,
    ) = userCoupon.use(order, LocalDateTime.now(clock))

    /** 주문 취소. 쓴 쿠폰을 돌려준다. */
    fun restore(order: Order) {
        val id = order.userCouponId ?: return
        userCouponRwRepository.findByIdForUpdate(id)?.takeIf { it.orderId == order.id }?.restore()
    }

    private fun linesOf(items: List<OrderLineCommand>): List<CouponLine> {
        val quantities = items.groupBy { it.skuId }.mapValues { (_, l) -> l.sumOf { it.quantity } }
        val skus = productSkuRwRepository.findAllById(quantities.keys)
        return skus.map { sku -> CouponLine(sku.product, (sku.product.price + sku.extraPrice) * (quantities[sku.id] ?: 0)) }
    }
}
