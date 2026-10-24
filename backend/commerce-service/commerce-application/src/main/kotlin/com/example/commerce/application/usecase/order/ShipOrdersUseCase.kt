package com.example.commerce.application.usecase.order

import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.service.OrderActor
import com.example.commerce.application.service.OrderCommandService
import com.example.commerce.application.usecase.result.ShipOrderOutcome
import com.example.commerce.application.usecase.result.ShipOrdersResult
import jakarta.persistence.EntityNotFoundException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 어드민 목록에서 고른 주문 여러 건을 한 번에 배송중으로. 건마다 따로 트랜잭션이라 하나가 실패해도 나머지는 된다. */
@Component
class ShipOrdersUseCase(
    private val orderCommandService: OrderCommandService,
    @Qualifier("rwTransactionManager") transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    data class Item(
        val orderId: Long,
        val carrier: String?,
        val trackingNo: String?,
    )

    fun execute(
        items: List<Item>,
        staffId: String?,
    ): ShipOrdersResult =
        ShipOrdersResult(
            items.map { item ->
                try {
                    val order =
                        tx.execute {
                            orderCommandService.changeStatus(
                                item.orderId,
                                OrderStatus.SHIPPING,
                                OrderActor.staff(staffId),
                                carrier = item.carrier,
                                trackingNo = item.trackingNo,
                            )
                        }
                    ShipOrderOutcome(item.orderId, true, null, order?.status)
                } catch (e: IllegalArgumentException) {
                    ShipOrderOutcome(item.orderId, false, e.message, null)
                } catch (e: EntityNotFoundException) {
                    ShipOrderOutcome(item.orderId, false, e.message, null)
                }
            },
        )

    companion object {
        const val MAX_ITEMS = 50
    }
}
