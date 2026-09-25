package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.AttendanceCheck
import com.example.commerce.application.domain.entity.EventKind
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.Promotion
import com.example.commerce.application.domain.entity.PromotionStatus
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.domain.repository.ro.AttendanceCheckRoRepository
import com.example.commerce.application.domain.repository.ro.ProductRoRepository
import com.example.commerce.application.domain.repository.ro.PromotionRoRepository
import com.example.commerce.application.domain.repository.rw.AttendanceCheckRwRepository
import com.example.commerce.application.domain.repository.rw.CouponRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.domain.repository.rw.PromotionRwRepository
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.usecase.command.PromotionCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** 오늘 이미 출석했다(409). */
class AlreadyCheckedInException : RuntimeException("오늘은 이미 출석했습니다.")

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class PromotionQueryService(
    private val promotionRoRepository: PromotionRoRepository,
    private val attendanceCheckRoRepository: AttendanceCheckRoRepository,
    private val productRoRepository: ProductRoRepository,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun banners(): List<Promotion> = promotionRoRepository.findBanners(today())

    /** 앱 상세. 노출이 꺼졌거나 지운 것은 없는 것으로 본다. */
    fun visible(id: Long): Promotion = promotionRoRepository.findById(id)?.takeIf { it.visible } ?: throw notFound(id)

    /** 기획전 상품. 관리자 순서대로, 지운 상품은 빠진다. */
    fun products(promotion: Promotion): List<Product> {
        val ids = promotion.productIds()
        if (ids.isEmpty()) return emptyList()
        val byId = productRoRepository.findAllByIdIn(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** 앱 기획전 상품: 판매 중인 것만. */
    fun sellingProducts(promotion: Promotion): List<Product> = products(promotion).filter { it.status == ProductStatus.SELLING }

    fun myCheckDates(
        promotionId: Long,
        userId: String,
    ): List<LocalDate> = attendanceCheckRoRepository.findAllByPromotionIdAndUserIdOrderByCheckDate(promotionId, userId).map { it.checkDate }

    fun adminPage(
        type: PromotionType?,
        q: String?,
        page: Int,
        size: Int,
    ): Page<Promotion> =
        promotionRoRepository.searchAdmin(
            type,
            q?.trim()?.takeIf { it.isNotEmpty() },
            PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)),
        )

    fun find(id: Long): Promotion = promotionRoRepository.findById(id) ?: throw notFound(id)

    fun attendanceCounts(ids: Collection<Long>): Map<Long, Long> =
        if (ids.isEmpty()) emptyMap() else attendanceCheckRoRepository.countByPromotionIds(ids).associate { it.promotionId to it.count }

    fun attendances(
        promotionId: Long,
        page: Int,
        size: Int,
    ): Page<AttendanceCheck> =
        attendanceCheckRoRepository.findAllByPromotionIdOrderByIdDesc(promotionId, PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)))

    companion object {
        fun notFound(id: Long) = EntityNotFoundException("id: $id 에 해당하는 기획전이 없습니다.")
    }
}

@Service
@Transactional(transactionManager = "rwTransactionManager")
class PromotionCommandService(
    private val promotionRwRepository: PromotionRwRepository,
    private val productRwRepository: ProductRwRepository,
    private val couponRwRepository: CouponRwRepository,
    private val clock: Clock,
) {
    fun create(command: PromotionCommand): Promotion {
        command.validate()
        val promotion = Promotion(command.type, command.title, command.startDate, command.endDate)
        promotion.initEventKind(command.eventKind)
        apply(promotion, command)
        return promotionRwRepository.saveAndFlush(promotion)
    }

    fun update(
        id: Long,
        command: PromotionCommand,
    ): Promotion {
        command.validate()
        val promotion = promotionRwRepository.findByIdOrNull(id) ?: throw PromotionQueryService.notFound(id)
        require(promotion.type == command.type) { "기획전·이벤트 종류는 바꿀 수 없습니다." }
        require(command.type != PromotionType.EVENT || command.eventKind == null || command.eventKind == promotion.kind()) {
            "이벤트 종류는 바꿀 수 없습니다."
        }
        promotion.initEventKind(promotion.kind())
        apply(promotion, command)
        return promotionRwRepository.saveAndFlush(promotion)
    }

    fun delete(id: Long) {
        val promotion = promotionRwRepository.findByIdOrNull(id) ?: throw PromotionQueryService.notFound(id)
        promotion.delete(LocalDateTime.now(clock))
    }

    private fun apply(
        promotion: Promotion,
        c: PromotionCommand,
    ) {
        promotion.update(
            c.title,
            c.subtitle,
            c.description,
            c.bannerImageUrl,
            c.bannerColor,
            c.startDate,
            c.endDate,
            c.visible,
            c.sortOrder,
        )
        val couponIds = if (promotion.kind() == EventKind.ATTENDANCE) emptyList() else c.couponIds
        val missingCoupons = couponIds.filter { couponRwRepository.findLive(it) == null }
        require(missingCoupons.isEmpty()) { "없는 쿠폰이 있습니다: ${missingCoupons.joinToString()}" }
        promotion.replaceCoupons(couponIds)
        when (promotion.type) {
            PromotionType.EXHIBITION -> {
                val found = productRwRepository.findAllById(c.productIds).mapNotNull { it.id }.toSet()
                val missing = c.productIds.filter { it !in found }
                require(missing.isEmpty()) { "없는 상품이 있습니다: ${missing.joinToString()}" }
                promotion.replaceProducts(c.productIds)
            }
            PromotionType.EVENT ->
                promotion.setReward(if (promotion.kind() == EventKind.ATTENDANCE) c.pointRuleCode else null, c.rewardPoints)
        }
    }
}

/**
 * 출석 체크. 출석 기록을 남기고, 이벤트에 보상 규칙이 있으면 point-service 로 적립한다(멱등 키 attend:<id>:<날짜>).
 * 적립 서버를 못 부르면 출석도 남기지 않는다(트랜잭션 롤백, 503) — 사용자가 다시 누르면 된다.
 * 한도 초과 같은 "적립 안 됨"은 출석은 남기고 0 P 와 이유를 돌려준다.
 */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class AttendanceService(
    private val promotionRwRepository: PromotionRwRepository,
    private val attendanceCheckRwRepository: AttendanceCheckRwRepository,
    private val pointGateway: PointGateway,
    private val clock: Clock,
) {
    fun check(
        userId: String,
        promotionId: Long,
    ): AttendanceOutcome {
        val promotion =
            promotionRwRepository.findByIdOrNull(promotionId)?.takeIf { it.visible } ?: throw PromotionQueryService.notFound(promotionId)
        require(promotion.kind() == EventKind.ATTENDANCE) { "출석 체크 이벤트가 아닙니다." }
        val today = LocalDate.now(clock)
        require(promotion.statusOn(today) == PromotionStatus.ONGOING) {
            "진행 중인 이벤트가 아닙니다."
        }
        if (attendanceCheckRwRepository.existsByPromotionIdAndUserIdAndCheckDate(
                promotionId,
                userId,
                today,
            )
        ) {
            throw AlreadyCheckedInException()
        }

        val check =
            try {
                attendanceCheckRwRepository.saveAndFlush(AttendanceCheck(promotion, userId, today))
            } catch (e: DataIntegrityViolationException) {
                // 같은 사람이 동시에 두 번 눌렀다. 유니크 제약이 하나만 남긴다.
                throw AlreadyCheckedInException()
            }

        val rule = promotion.pointRuleCode
        if (rule != null) {
            val earned = pointGateway.earn(userId, rule, "attend:$promotionId:$today", "출석 체크 · ${promotion.title}")
            check.rewarded(if (earned.applied) earned.amount else 0, if (earned.applied) null else skipMessage(earned.reason))
        }
        val dates = attendanceCheckRwRepository.findAllByPromotionIdAndUserIdOrderByCheckDate(promotionId, userId).map { it.checkDate }
        return AttendanceOutcome(today, check.rewardPoints, check.rewardMessage, dates)
    }

    private fun skipMessage(reason: String?): String =
        when (reason) {
            "DAILY_LIMIT" -> "오늘 받을 수 있는 포인트를 이미 받아 적립되지 않았습니다."
            "TOTAL_LIMIT" -> "받을 수 있는 포인트를 모두 받아 적립되지 않았습니다."
            "RULE_DISABLED" -> "지금은 출석 포인트를 적립하지 않습니다."
            "DUPLICATE" -> "이미 적립된 출석입니다."
            else -> "포인트 적립 규칙을 찾을 수 없어 적립되지 않았습니다."
        }
}

data class AttendanceOutcome(
    val checkedDate: LocalDate,
    val rewardPoints: Long,
    val rewardMessage: String?,
    val checkedDates: List<LocalDate>,
)
