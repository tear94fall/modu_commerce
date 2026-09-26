package com.example.commerce.application.usecase.promotion

import com.example.commerce.application.domain.entity.EventKind
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.service.AttendanceService
import com.example.commerce.application.service.CouponQueryService
import com.example.commerce.application.service.PromotionCacheService
import com.example.commerce.application.service.PromotionCaches
import com.example.commerce.application.service.PromotionCommandService
import com.example.commerce.application.service.PromotionQueryService
import com.example.commerce.application.service.WishlistQueryService
import com.example.commerce.application.usecase.command.PromotionCommand
import com.example.commerce.application.usecase.coupon.adminSummaries
import com.example.commerce.application.usecase.coupon.offers
import com.example.commerce.application.usecase.result.AdminAttendanceResult
import com.example.commerce.application.usecase.result.AdminPromotionDetailResult
import com.example.commerce.application.usecase.result.AdminPromotionSummaryResult
import com.example.commerce.application.usecase.result.AttendanceInfoResult
import com.example.commerce.application.usecase.result.AttendanceResult
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.ProductSummaryResult
import com.example.commerce.application.usecase.result.PromotionBannerResult
import com.example.commerce.application.usecase.result.PromotionDetailResult
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetPromotionBannersUseCase(
    private val promotionQueryService: PromotionQueryService,
    private val promotionCacheService: PromotionCacheService,
) {
    /** 캐시(오늘 날짜 키)에서 읽는다. 앱을 열 때마다 불리는 가장 뜨거운 조회다. */
    fun execute(): List<PromotionBannerResult> = promotionCacheService.banners(promotionQueryService.today())
}

@Component
class GetPromotionUseCase(
    private val promotionQueryService: PromotionQueryService,
    private val promotionCacheService: PromotionCacheService,
    private val wishlistQueryService: WishlistQueryService,
    private val couponQueryService: CouponQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        id: Long,
    ): PromotionDetailResult {
        // 공통 정보는 캐시에서, 상품 가격·품절·쿠폰 수량·찜·출석은 매번 새로 읽는다.
        val p = promotionCacheService.snapshot(id)?.takeIf { it.visible } ?: throw PromotionQueryService.notFound(id)
        val today = promotionQueryService.today()
        val products =
            if (p.type == PromotionType.EXHIBITION) {
                val list = promotionQueryService.sellingProducts(p.productIds)
                val wished = wishlistQueryService.wishedIds(userId, list.mapNotNull { it.id })
                list.map { ProductSummaryResult.from(it, wished = it.id in wished) }
            } else {
                emptyList()
            }
        val attendance =
            if (p.eventKind == EventKind.ATTENDANCE) {
                val dates = promotionQueryService.myCheckDates(id, userId)
                AttendanceInfoResult(p.rewardPoints, today, today in dates, dates, p.totalDays())
            } else {
                null
            }
        return PromotionDetailResult(
            p.id,
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
            p.eventKind,
            couponQueryService.offers(userId, couponQueryService.live(p.couponIds)),
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
    private val couponQueryService: CouponQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(id: Long): AdminPromotionDetailResult {
        val p = promotionQueryService.find(id)
        val coupons = couponQueryService.adminSummaries(couponQueryService.live(p.couponIds))
        return AdminPromotionDetailResult.from(
            p,
            promotionQueryService.today(),
            promotionQueryService.attendanceCounts(listOf(id))[id] ?: 0,
            promotionQueryService.products(p),
            coupons,
        )
    }
}

/**
 * 만들기·고치기·지우기. 캐시는 여기(트랜잭션 밖)서 비운다: 서비스 트랜잭션이 커밋된 뒤라 옛 값이 다시 들어가지 않고,
 * Redis 가 죽어 비우기가 실패해도 이미 커밋된 변경이 오류로 보이지 않는다(캐시 오류 처리기가 로그만 남긴다, 최대 TTL 만큼 늦게 반영).
 */
@Component
class SavePromotionUseCase(
    private val promotionCommandService: PromotionCommandService,
    private val getAdminPromotionUseCase: GetAdminPromotionUseCase,
) {
    @CacheEvict(cacheNames = [PromotionCaches.BANNERS], allEntries = true)
    fun create(command: PromotionCommand): AdminPromotionDetailResult {
        val id = requireNotNull(promotionCommandService.create(command).id)
        return getAdminPromotionUseCase.execute(id)
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = [PromotionCaches.BANNERS], allEntries = true),
            CacheEvict(cacheNames = [PromotionCaches.PROMOTION], key = "#p0"),
        ],
    )
    fun update(
        id: Long,
        command: PromotionCommand,
    ): AdminPromotionDetailResult {
        promotionCommandService.update(id, command)
        return getAdminPromotionUseCase.execute(id)
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = [PromotionCaches.BANNERS], allEntries = true),
            CacheEvict(cacheNames = [PromotionCaches.PROMOTION], key = "#p0"),
        ],
    )
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
