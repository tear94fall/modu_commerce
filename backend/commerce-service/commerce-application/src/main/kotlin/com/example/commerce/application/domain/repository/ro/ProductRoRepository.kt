package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductStatus
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductRoRepository :
    RoRepository<Product, Long>,
    ProductCustomRepository {
    fun findAllByOrderByIdAsc(): List<Product>

    fun findById(id: Long): Product?

    /** 지운 상품은 @SQLRestriction 으로 빠진다. 순서는 보장하지 않는다. */
    fun findAllByIdIn(ids: Collection<Long>): List<Product>

    /** 판매 중인데 모든 SKU 재고가 0 인(= 품절) 상품. 찜이 많은 것부터 — 재입고 우선순위다. */
    @Query(
        "select p from Product p where p.deletedAt is null and p.status = :status " +
            "and not exists (select 1 from ProductSku s where s.product = p and s.stock > 0) " +
            "order by p.wishCount desc, p.id desc",
    )
    fun findSoldOut(
        @Param("status") status: ProductStatus,
    ): List<Product>

    @Query("select count(p) from Product p where p.deletedAt is null and p.status = :status")
    fun countByStatus(
        @Param("status") status: ProductStatus,
    ): Long
}
