package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.AttendanceCheck
import com.example.commerce.application.domain.entity.Promotion
import com.example.commerce.application.domain.entity.PromotionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface PromotionRoRepository : RoRepository<Promotion, Long> {
    fun findById(id: Long): Promotion?

    /** 앱 배너: 노출 중이고 오늘이 기간 안인 것. 순서, 최신 순. */
    @Query(
        "select p from Promotion p where p.visible = true and p.startDate <= :today and p.endDate >= :today " +
            "order by p.sortOrder asc, p.id desc",
    )
    fun findBanners(
        @Param("today") today: LocalDate,
    ): List<Promotion>

    /** 백오피스 목록. 종류·제목(부분 일치)으로 거른다. */
    @Query(
        "select p from Promotion p where (:type is null or p.type = :type) " +
            "and (:q is null or lower(p.title) like lower(concat('%', :q, '%'))) order by p.sortOrder asc, p.id desc",
    )
    fun searchAdmin(
        @Param("type") type: PromotionType?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<Promotion>
}

/** 이벤트별 출석 수. */
data class PromotionCount(
    val promotionId: Long,
    val count: Long,
)

interface AttendanceCheckRoRepository : RoRepository<AttendanceCheck, Long> {
    fun findAllByPromotionIdAndUserIdOrderByCheckDate(
        promotionId: Long,
        userId: String,
    ): List<AttendanceCheck>

    fun findAllByPromotionIdOrderByIdDesc(
        promotionId: Long,
        pageable: Pageable,
    ): Page<AttendanceCheck>

    @Query(
        "select new com.example.commerce.application.domain.repository.ro.PromotionCount(a.promotion.id, count(a)) " +
            "from AttendanceCheck a where a.promotion.id in :ids group by a.promotion.id",
    )
    fun countByPromotionIds(
        @Param("ids") ids: Collection<Long>,
    ): List<PromotionCount>
}
