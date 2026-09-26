package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.EventKind
import com.example.commerce.application.domain.entity.Promotion
import com.example.commerce.application.domain.entity.PromotionStatus
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.domain.repository.ro.PromotionRoRepository
import com.example.commerce.application.usecase.result.PromotionBannerResult
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 캐시 이름. 캐시 매니저(commerce-api CacheConfig)가 이름마다 TTL·직렬화를 정한다. */
object PromotionCaches {
    /** 홈 배너 목록. 키 = 오늘(KST) 날짜. 자정이 지나면 새 키라 기간이 바뀐 배너가 저절로 반영된다. */
    const val BANNERS = "promotion-banners"

    /** 기획전·이벤트 한 건의 공통 정보. 키 = id. */
    const val PROMOTION = "promotion"
}

/**
 * 기획전·이벤트 한 건에서 사람마다 같은 부분만 모은 스냅숏(캐시에 넣는 값).
 * 상품 가격·품절, 쿠폰 수량, 찜·출석·받은 쿠폰처럼 자주 바뀌거나 사람마다 다른 값은 넣지 않고 매번 DB 에서 읽는다.
 */
data class PromotionSnapshot(
    val id: Long,
    val type: PromotionType,
    val eventKind: EventKind?,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val bannerImageUrl: String?,
    val bannerColor: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val visible: Boolean,
    val productIds: List<Long>,
    val couponIds: List<Long>,
    val rewardPoints: Long?,
) {
    fun statusOn(today: LocalDate): PromotionStatus =
        when {
            today.isBefore(startDate) -> PromotionStatus.UPCOMING
            today.isAfter(endDate) -> PromotionStatus.ENDED
            else -> PromotionStatus.ONGOING
        }

    fun totalDays(): Int = (endDate.toEpochDay() - startDate.toEpochDay() + 1).toInt()

    companion object {
        fun from(p: Promotion) =
            PromotionSnapshot(
                id = requireNotNull(p.id),
                type = p.type,
                eventKind = p.kind(),
                title = p.title,
                subtitle = p.subtitle,
                description = p.description,
                bannerImageUrl = p.bannerImageUrl,
                bannerColor = p.bannerColor,
                startDate = p.startDate,
                endDate = p.endDate,
                visible = p.visible,
                productIds = p.productIds(),
                couponIds = p.couponIds.toList(),
                rewardPoints = p.rewardPoints,
            )
    }
}

/**
 * 앱이 기획전·이벤트를 읽는 캐시 경로. 백오피스가 만들기·고치기·지우기를 하면 [PromotionCommandService] 가 비운다
 * (캐시 매니저가 트랜잭션을 알아서 커밋 뒤에 비운다 — 커밋 전 옛 값이 다시 캐시에 들어가지 않게).
 * 키 식은 파라미터 이름 대신 위치(#p0)를 쓴다. 코틀린은 기본으로 파라미터 이름을 클래스 파일에 남기지 않는다.
 */
@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class PromotionCacheService(
    private val promotionRoRepository: PromotionRoRepository,
) {
    @Cacheable(cacheNames = [PromotionCaches.BANNERS], key = "#p0.toString()")
    fun banners(today: LocalDate): List<PromotionBannerResult> = promotionRoRepository.findBanners(today).map(PromotionBannerResult::from)

    /** 없거나 지운 것은 null(캐시하지 않는다). 노출 여부는 호출하는 쪽이 본다. */
    @Cacheable(cacheNames = [PromotionCaches.PROMOTION], key = "#p0", unless = "#result == null")
    fun snapshot(id: Long): PromotionSnapshot? = promotionRoRepository.findById(id)?.let(PromotionSnapshot::from)
}
