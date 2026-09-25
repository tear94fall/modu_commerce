package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.AttendanceCheck
import com.example.commerce.application.domain.entity.Promotion
import java.time.LocalDate

interface PromotionRwRepository : RwRepository<Promotion, Long>

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
