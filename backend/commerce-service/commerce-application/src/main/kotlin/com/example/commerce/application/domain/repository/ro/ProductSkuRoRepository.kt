package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.ProductSku
import com.example.commerce.application.domain.entity.ProductStatus
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductSkuRoRepository : RoRepository<ProductSku, Long> {
    /**
     * 판매 중 상품의 SKU 중 재고가 [threshold] 이하인 것. 상품·옵션 값까지 함께 읽어 화면용 이름을 바로 만든다.
     * 재고가 적은 것부터, 같은 재고면 최근 상품부터.
     */
    @Query(
        "select s from ProductSku s join fetch s.product p left join fetch s.optionValues ov left join fetch ov.group " +
            "where p.deletedAt is null and p.status = :status and s.stock <= :threshold " +
            "order by s.stock asc, p.id desc, s.id asc",
    )
    fun findLowStock(
        @Param("threshold") threshold: Int,
        @Param("status") status: ProductStatus,
    ): List<ProductSku>

    /** 판매 중 상품의 SKU 중 재고가 [threshold] 이상인 것(= 과다). 재고가 많은 것부터. */
    @Query(
        "select s from ProductSku s join fetch s.product p left join fetch s.optionValues ov left join fetch ov.group " +
            "where p.deletedAt is null and p.status = :status and s.stock >= :threshold " +
            "order by s.stock desc, p.id desc, s.id asc",
    )
    fun findOverstock(
        @Param("threshold") threshold: Int,
        @Param("status") status: ProductStatus,
    ): List<ProductSku>
}
