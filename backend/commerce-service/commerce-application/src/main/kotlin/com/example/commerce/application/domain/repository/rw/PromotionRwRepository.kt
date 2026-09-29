package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.AttendanceCheck
import com.example.commerce.application.domain.entity.Promotion
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface PromotionRwRepository : RwRepository<Promotion, Long> {
    /** 앱 배너(캐시 채우기용 master 조회). [com.example.commerce.application.domain.repository.ro.PromotionRoRepository.findBanners] 와 같은 조건. */
    @Query(
        "select p from Promotion p where p.visible = true and p.startDate <= :today and p.endDate >= :today " +
            "order by p.sortOrder asc, p.id desc",
    )
    fun findBanners(
        @Param("today") today: LocalDate,
    ): List<Promotion>
}

interface AttendanceCheckRwRepository : RwRepository<AttendanceCheck, Long> {
    fun existsByPromotionIdAndUserIdAndCheckDate(
        promotionId: Long,
        userId: String,
        checkDate: LocalDate,
    ): Boolean

    fun findAllByPromotionIdAndUserIdOrderByCheckDate(
        promotionId: Long,
        userId: String,
    ): List<AttendanceCheck>
}
