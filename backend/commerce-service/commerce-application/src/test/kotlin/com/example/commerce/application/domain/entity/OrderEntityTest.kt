package com.example.commerce.application.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderEntityTest {
    private fun tshirt(): Product =
        Product.create(name = "티셔츠", description = "", price = 19_000).apply {
            replaceOptions(
                listOf(OptionGroupSpec("사이즈", listOf("M", "L"))),
                listOf(SkuSpec(mapOf("사이즈" to "M"), 0, 5), SkuSpec(mapOf("사이즈" to "L"), 1_000, 1)),
            )
        }

    private val address =
        Address(
            userId = "u1",
            recipient = "임",
            phone = "010-1111-2222",
            zipCode = "06236",
            address1 = "서울",
            address2 = null,
            isDefault = true,
        )

    @Test
    fun `주문 항목은 단가 스냅샷을 갖고 합계를 더한다`() {
        val product = tshirt()
        val order = Order.create("u1", address)
        order.addItem(product.skus[0], 2)
        order.addItem(product.skus[1], 1)

        assertEquals(19_000L * 2 + 20_000L, order.totalAmount)
        assertEquals("L", order.items[1].optionLabel)
        assertEquals(20_000L, order.items[1].unitPrice)
        assertEquals(OrderStatus.PAID, order.status)
        assertTrue(order.orderNo.matches(Regex("\\d{8}-[A-Z2-9]{6}")))
        assertEquals("서울", order.address1)
    }

    @Test
    fun `상태 전이는 허용된 것만`() {
        val order = Order.create("u1", address)
        assertThrows(IllegalArgumentException::class.java) { order.transition(OrderStatus.DELIVERED) }
        order.transition(OrderStatus.SHIPPING)
        assertThrows(IllegalArgumentException::class.java) { order.cancel() }
        order.transition(OrderStatus.DELIVERED)
        assertFalse(order.status.canTransitionTo(OrderStatus.CANCELLED))

        val fresh = Order.create("u1", address)
        fresh.cancel()
        assertTrue(fresh.isCancelled())
        assertTrue(fresh.cancelledAt != null)
    }

    @Test
    fun `재고는 모자라면 거부하고 취소하면 돌아온다`() {
        val sku = tshirt().skus[1] // L, 재고 1
        assertThrows(IllegalArgumentException::class.java) { sku.decreaseStock(2) }
        sku.decreaseStock(1)
        assertTrue(sku.isSoldOut())
        sku.increaseStock(1)
        assertEquals(1, sku.stock)
        assertEquals("티셔츠 (L)", sku.displayName())
    }

    @Test
    fun `장바구니 수량은 1~99 이고 같은 SKU 는 더해진다`() {
        val item = CartItem(userId = "u1", sku = tshirt().skus[0], quantity = 1)
        item.add(2)
        assertEquals(3, item.quantity)
        assertEquals(57_000L, item.lineAmount())
        assertThrows(IllegalArgumentException::class.java) { item.changeQuantity(0) }
        assertThrows(IllegalArgumentException::class.java) { item.add(97) }
        assertTrue(item.isAvailable())
    }
}
