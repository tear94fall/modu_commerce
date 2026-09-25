package com.example.commerce.application.usecase.promotion

import com.example.commerce.application.domain.entity.Promotion
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.service.AttendanceService
import com.example.commerce.application.service.PromotionCommandService
import com.example.commerce.application.service.PromotionQueryService
import com.example.commerce.application.service.WishlistQueryService
import com.example.commerce.application.usecase.command.PromotionCommand
import com.example.commerce.application.usecase.result.AdminAttendanceResult
import com.example.commerce.application.usecase.result.AdminPromotionDetailResult
import com.example.commerce.application.usecase.result.AdminPromotionSummaryResult
import com.example.commerce.application.usecase.result.AttendanceInfoResult
import com.example.commerce.application.usecase.result.AttendanceResult
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.ProductSummaryResult
import com.example.commerce.application.usecase.result.PromotionBannerResult
import com.example.commerce.application.usecase.result.PromotionDetailResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetPromotionBannersUseCase(
    private val promotionQueryService: PromotionQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(): List<PromotionBannerResult> = promotionQueryService.banners().map(PromotionBannerResult::from)
}

@Component
class GetPromotionUseCase(
    private val promotionQueryService: PromotionQueryService,
    private val wishlistQueryService: WishlistQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        id: Long,
    ): PromotionDetailResult {
        val p = promotionQueryService.visible(id)
        val today = promotionQueryService.today()
        val products =
            if (p.type == PromotionType.EXHIBITION) {
                val list = promotionQueryService.sellingProducts(p)
                val wished = wishlistQueryService.wishedIds(userId, list.mapNotNull { it.id })
                list.map { ProductSummaryResult.from(it, wished = it.id in wished) }
            } else {
                emptyList()
            }
        val attendance =
            if (p.type == PromotionType.EVENT) {
                val dates = promotionQueryService.myCheckDates(id, userId)
                AttendanceInfoResult(p.rewardPoints, today, today in dates, dates, p.totalDays())
            } else {
                null
            }
        return PromotionDetailResult(
            requireNotNull(p.id),
            p.type,
            p.title,
            p.subtitle,
            p.description,
            p.bannerImageUrl,
            p.bannerColor,
            p.startDate,
            p.endDate,
            p.statusOn(today),
            products,
            attendance,
        )
    }
}

@Component
class CheckAttendanceUseCase(
    private val attendanceService: AttendanceService,
) {
    fun execute(
        userId: String,
        promotionId: Long,
    ): AttendanceResult {
        val o = attendanceService.check(userId, promotionId)
        return AttendanceResult(o.checkedDate, o.rewardPoints, o.rewardMessage, o.checkedDates)
    }
}

@Component
class SearchAdminPromotionsUseCase(
    private val promotionQueryService: PromotionQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        type: PromotionType?,
        q: String?,
        page: Int,
        size: Int,
    ): PageResult<AdminPromotionSummaryResult> {
        val result = promotionQueryService.adminPage(type, q, page, size)
        val counts = promotionQueryService.attendanceCounts(result.content.mapNotNull { it.id })
        val today = promotionQueryService.today()
        return PageResult.from(result) { AdminPromotionSummaryResult.from(it, today, counts[it.id] ?: 0) }
    }
}

@Component
class GetAdminPromotionUseCase(
    private val promotionQueryService: PromotionQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(id: Long): AdminPromotionDetailResult = detail(promotionQueryService, promotionQueryService.find(id))
}

@Component
class SavePromotionUseCase(
    private val promotionCommandService: PromotionCommandService,
    private val getAdminPromotionUseCase: GetAdminPromotionUseCase,
) {
    fun create(command: PromotionCommand): AdminPromotionDetailResult {
        val id = requireNotNull(promotionCommandService.create(command).id)
        return getAdminPromotionUseCase.execute(id)
    }

    fun update(
        id: Long,
        command: PromotionCommand,
    ): AdminPromotionDetailResult {
        promotionCommandService.update(id, command)
        return getAdminPromotionUseCase.execute(id)
    }

    fun delete(id: Long) = promotionCommandService.delete(id)
}

@Component
class GetPromotionAttendancesUseCase(
    private val promotionQueryService: PromotionQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        id: Long,
        page: Int,
        size: Int,
    ): PageResult<AdminAttendanceResult> {
        promotionQueryService.find(id)
        return PageResult.from(promotionQueryService.attendances(id, page, size), AdminAttendanceResult::from)
    }
}

private fun detail(
    q: PromotionQueryService,
    p: Promotion,
): AdminPromotionDetailResult {
    val id = requireNotNull(p.id)
    return AdminPromotionDetailResult.from(p, q.today(), q.attendanceCounts(listOf(id))[id] ?: 0, q.products(p))
}
