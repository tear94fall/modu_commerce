package com.example.commerce.application.usecase.stock

import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.repository.ro.OrderRoRepository
import com.example.commerce.application.domain.repository.ro.ProductRoRepository
import com.example.commerce.application.domain.repository.ro.ProductSkuRoRepository
import com.example.commerce.application.usecase.result.StockAlertResult
import com.example.commerce.application.usecase.result.StockProductResult
import com.example.commerce.application.usecase.result.StockSkuResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 백오피스 재고 경고. 부족·과다 기준은 요청으로 받고, 없으면 설정
 * `modu.commerce.stock.low-threshold`(기본 5)·`modu.commerce.stock.high-threshold`(기본 100).
 * 최근 30일 판매 수량을 줄마다 붙인다(주문 paidAt 은 UTC 저장). 전부 replica 에서 읽는다.
 */
@Component
class GetStockAlertsUseCase(
    private val products: ProductRoRepository,
    private val skus: ProductSkuRoRepository,
    private val orders: OrderRoRepository,
    private val clock: Clock,
    @Value("\${modu.commerce.stock.low-threshold:$DEFAULT_LOW}") private val defaultLow: Int,
    @Value("\${modu.commerce.stock.high-threshold:$DEFAULT_HIGH}") private val defaultHigh: Int,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        low: Int?,
        high: Int?,
    ): StockAlertResult {
        val lowLimit = low ?: defaultLow
        val highLimit = high ?: defaultHigh
        require(lowLimit in 0..MAX_LOW) { "부족 기준은 0~$MAX_LOW 사이여야 합니다." }
        require(highLimit in 1..MAX_HIGH) { "과다 기준은 1~$MAX_HIGH 사이여야 합니다." }
        require(highLimit > lowLimit) { "과다 기준은 부족 기준보다 커야 합니다." }

        val soldOut = products.findSoldOut(ProductStatus.SELLING)
        val soldOutIds = soldOut.mapNotNull { it.id }.toSet()
        val lowSkus = skus.findLowStock(lowLimit, ProductStatus.SELLING).filter { it.product.id !in soldOutIds }
        val overSkus = skus.findOverstock(highLimit, ProductStatus.SELLING)

        val from = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(SALES_DAYS.toLong())
        val soldBySku =
            orders.findSoldQuantityBySku(from, OrderStatus.CANCELLED).associate { (it[0] as Number).toLong() to (it[1] as Number).toInt() }
        val soldOf = { id: Long? -> id?.let { soldBySku[it] } ?: 0 }

        return StockAlertResult(
            low = lowLimit,
            high = highLimit,
            salesDays = SALES_DAYS,
            sellingProducts = products.countByStatus(ProductStatus.SELLING),
            soldOut = soldOut.map { p -> StockProductResult.from(p, p.skus.sumOf { soldOf(it.id) }) },
            lowStock = lowSkus.map { StockSkuResult.from(it, soldOf(it.id)) },
            overstock = overSkus.map { StockSkuResult.from(it, soldOf(it.id)) },
        )
    }

    companion object {
        const val DEFAULT_LOW = 5
        const val DEFAULT_HIGH = 100
        const val MAX_LOW = 1_000
        const val MAX_HIGH = 100_000
        const val SALES_DAYS = 30
    }
}
