package com.example.commerce.application.usecase.stats

import com.example.commerce.application.config.ClockConfig
import com.example.commerce.application.domain.entity.CouponSource
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.domain.repository.ro.AttendanceCheckRoRepository
import com.example.commerce.application.domain.repository.ro.CouponRoRepository
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.PromotionRoRepository
import com.example.commerce.application.domain.repository.ro.UserCouponRoRepository
import com.example.commerce.application.usecase.result.AttendancePerformance
import com.example.commerce.application.usecase.result.DayCount
import com.example.commerce.application.usecase.result.EventCouponPerformance
import com.example.commerce.application.usecase.result.ExhibitionPerformance
import com.example.commerce.application.usecase.result.ExhibitionProductPerformance
import com.example.commerce.application.usecase.result.PromotionPerformanceResult
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 백오피스 기획전·이벤트 상세의 성과. 기획전은 기간(한국 달력 날짜) 안에 결제된 주문 중 기획전 상품이 든 것, 출석 이벤트는 출석 기록,
 * 쿠폰 받기 이벤트는 이벤트로 나간 쿠폰(source = EVENT). 전부 replica.
 */
@Component
class GetPromotionPerformanceUseCase(
    private val promotions: PromotionRoRepository,
    private val orders: OrderRoRepository,
    private val attendances: AttendanceCheckRoRepository,
    private val coupons: CouponRoRepository,
    private val userCoupons: UserCouponRoRepository,
    private val clock: Clock,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(promotionId: Long): PromotionPerformanceResult {
        val p =
            promotions.findById(promotionId)?.takeIf { it.deletedAt == null }
                ?: throw EntityNotFoundException("기획전·이벤트가 없습니다: $promotionId")
        return when {
            p.type == PromotionType.EXHIBITION -> {
                PromotionPerformanceResult(promotionId, exhibition(p.products.map { it.productId }, p.startDate, p.endDate), null, null)
            }

            p.couponIds.isNotEmpty() -> {
                PromotionPerformanceResult(promotionId, null, null, eventCoupons(p.couponIds))
            }

            else -> {
                PromotionPerformanceResult(promotionId, null, attendance(promotionId, p.startDate, p.endDate), null)
            }
        }
    }

    private fun exhibition(
        productIds: List<Long>,
        start: LocalDate,
        end: LocalDate,
    ): ExhibitionPerformance {
        if (productIds.isEmpty()) return ExhibitionPerformance(0, 0, 0, 0, emptyList())
        // 기간은 한국 달력 날짜, 결제 시각은 UTC.
        val from = start.atStartOfDay(ClockConfig.KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        val to =
            end
                .plusDays(1)
                .atStartOfDay(ClockConfig.KST)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
        val rows = orders.findPromotionItemRows(productIds, from, to).map { ItemRow.of(it) }
        val cancelledOrderIds = rows.filter { it.status == OrderStatus.CANCELLED }.map { it.orderId }.toSet()
        val live = rows.filter { it.orderId !in cancelledOrderIds }
        val products =
            live
                .groupBy { it.productId }
                .map { (id, items) ->
                    ExhibitionProductPerformance(
                        id,
                        items.first().productName,
                        items.sumOf { it.quantity.toLong() },
                        items.sumOf {
                            it.unitPrice *
                                it.quantity
                        },
                    )
                }.sortedByDescending { it.sales }
        return ExhibitionPerformance(
            orders =
                live
                    .map { it.orderId }
                    .distinct()
                    .size
                    .toLong(),
            cancelledOrders = cancelledOrderIds.size.toLong(),
            units = live.sumOf { it.quantity.toLong() },
            sales = live.sumOf { it.unitPrice * it.quantity },
            products = products,
        )
    }

    /** 날짜별 출석은 이벤트 시작일부터 종료일(아직 진행 중이면 오늘, 한국 날짜)까지 하루도 빠짐없이 — 출석 없는 날은 0. */
    private fun attendance(
        promotionId: Long,
        start: LocalDate,
        end: LocalDate,
    ): AttendancePerformance {
        val rows = attendances.findRows(promotionId)
        val counts = rows.groupingBy { it[1] as LocalDate }.eachCount()
        val today = clock.instant().atZone(ClockConfig.KST).toLocalDate()
        val last = minOf(end, maxOf(today, start))
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        return AttendancePerformance(
            participants =
                rows
                    .map { it[0] as String }
                    .distinct()
                    .size
                    .toLong(),
            checks = rows.size.toLong(),
            rewardPoints = rows.sumOf { (it[2] as Number).toLong() },
            byDay = days.map { DayCount(it, (counts[it] ?: 0).toLong()) },
        )
    }

    private fun eventCoupons(couponIds: List<Long>): List<EventCouponPerformance> {
        val names = coupons.findLiveByIds(couponIds).associate { requireNotNull(it.id) to it.name }
        val counts = userCoupons.countEventIssues(couponIds, CouponSource.EVENT).associate { (it[0] as Number).toLong() to it }
        return couponIds.map { id ->
            val row = counts[id]
            EventCouponPerformance(
                id,
                names[id] ?: "(지운 쿠폰)",
                (row?.get(1) as Number?)?.toLong() ?: 0L,
                (row?.get(2) as Number?)?.toLong() ?: 0L,
            )
        }
    }

    private data class ItemRow(
        val orderId: Long,
        val status: OrderStatus,
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val unitPrice: Long,
    ) {
        companion object {
            fun of(r: Array<Any?>) =
                ItemRow(
                    (r[0] as Number).toLong(),
                    r[1] as OrderStatus,
                    (r[2] as Number).toLong(),
                    r[3] as String,
                    (r[4] as Number).toInt(),
                    (r[5] as Number).toLong(),
                )
        }
    }
}
