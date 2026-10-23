package com.example.commerce.application.usecase.stats

import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.service.DayWindow
import com.example.commerce.application.usecase.result.AdminStatsDay
import com.example.commerce.application.usecase.result.AdminStatsResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/** 백오피스 홈의 커머스 숫자. 주문의 paidAt·cancelledAt(UTC)을 표시 시간대의 날짜로 묶는다. 전부 replica. */
@Component
class GetAdminStatsUseCase(
    private val orders: OrderRoRepository,
    private val clock: Clock,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        days: Int,
        zone: ZoneId,
    ): AdminStatsResult {
        val window = DayWindow(zone, days, clock)
        val rows = orders.findStatsRowsBetween(window.fromStored, window.toStored).map { Row.of(it) }
        val paid = rows.groupBy { window.dateOf(it.paidAt) }
        val cancelled =
            rows.filter { it.status == OrderStatus.CANCELLED && it.cancelledAt != null }.groupBy {
                window.dateOf(
                    it.cancelledAt!!,
                )
            }
        return AdminStatsResult(
            zone = zone.id,
            days =
                window.dates.map { date ->
                    val day = paid[date].orEmpty()
                    AdminStatsDay(
                        date = date,
                        orders = day.size,
                        amount = day.sumOf { it.totalAmount - it.couponDiscount - it.pointAmount },
                        couponUsed = day.count { it.usedCoupon },
                        cancelled = cancelled[date]?.size ?: 0,
                    )
                },
        )
    }

    private data class Row(
        val paidAt: LocalDateTime,
        val status: OrderStatus,
        val totalAmount: Long,
        val couponDiscount: Long,
        val pointAmount: Long,
        val usedCoupon: Boolean,
        val cancelledAt: LocalDateTime?,
    ) {
        companion object {
            fun of(r: Array<Any?>): Row =
                Row(
                    paidAt = r[0] as LocalDateTime,
                    status = r[1] as OrderStatus,
                    totalAmount = (r[2] as Number).toLong(),
                    couponDiscount = (r[3] as Number).toLong(),
                    pointAmount = (r[4] as Number).toLong(),
                    usedCoupon = r[5] != null,
                    cancelledAt = r[6] as LocalDateTime?,
                )
        }
    }
}
