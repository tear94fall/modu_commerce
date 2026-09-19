package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.entity.CartItem
import com.example.commerce.application.domain.entity.Order
import com.example.commerce.application.domain.entity.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CartItemRoRepository : RoRepository<CartItem, Long> {
    fun findAllByUserIdOrderByIdDesc(userId: String): List<CartItem>
}

interface AddressRoRepository : RoRepository<Address, Long> {
    fun findAllByUserIdOrderByIsDefaultDescIdDesc(userId: String): List<Address>

    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Address?
}

interface OrderCustomRepository {
    /** 어드민 목록. 상태·주문번호(부분 일치)로 거른다. 최신부터. */
    fun searchAdminPage(
        status: OrderStatus?,
        orderNo: String?,
        pageable: Pageable,
    ): Page<Order>
}

interface OrderRoRepository :
    RoRepository<Order, Long>,
    OrderCustomRepository {
    fun findAllByUserIdOrderByIdDesc(
        userId: String,
        pageable: Pageable,
    ): Page<Order>

    fun findByIdAndUserId(
        id: Long,
        userId: String,
    ): Order?

    fun findById(id: Long): Order?
}
