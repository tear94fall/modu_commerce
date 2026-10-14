package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Product
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/**
 * 찜 수·리뷰 수·별점 합은 엔티티가 쓰지 않는다(updatable = false). 여러 요청이 동시에 바꿔도 잃지 않게 여기의 원자적 UPDATE 로만 바꾼다.
 * 네이티브 SQL 인 까닭: JPQL 로 쓰면 Hibernate 가 파라미터를 컬럼의 columnDefinition("bigint not null default 0")으로 cast 해 SQL 이 깨진다.
 * 반영된 영속성 컨텍스트는 비우지 않는다(같은 트랜잭션의 다른 엔티티가 살아 있어야 한다). 화면에 돌려줄 값은 호출자가 엔티티에도 같이 더한다.
 */
interface ProductRwRepository : RwRepository<Product, Long> {
    /** @SQLRestriction 을 우회해 삭제된 행까지 센다. 시더가 "한 번이라도 상품이 있었는지" 볼 때 쓴다. */
    @Query(value = "select count(*) from products", nativeQuery = true)
    fun countIncludingDeleted(): Long

    @Query("select count(p) from Product p where p.category.id = :categoryId")
    fun countByCategory(categoryId: Long): Long

    @Modifying(flushAutomatically = true)
    @Transactional(transactionManager = "rwTransactionManager")
    @Query(value = "update products set wish_count = wish_count + 1 where id = :id", nativeQuery = true)
    fun incrementWishCount(
        @Param("id") id: Long,
    ): Int

    @Modifying(flushAutomatically = true)
    @Transactional(transactionManager = "rwTransactionManager")
    @Query(
        value = "update products set wish_count = case when wish_count > 0 then wish_count - 1 else 0 end where id = :id",
        nativeQuery = true,
    )
    fun decrementWishCount(
        @Param("id") id: Long,
    ): Int

    /** 노출 리뷰 하나 추가(작성·숨김 해제). */
    @Modifying(flushAutomatically = true)
    @Transactional(transactionManager = "rwTransactionManager")
    @Query(
        value = "update products set rating_sum = rating_sum + :rating, review_count = review_count + 1 where id = :id",
        nativeQuery = true,
    )
    fun addRating(
        @Param("id") id: Long,
        @Param("rating") rating: Long,
    ): Int

    /**
     * 노출 리뷰 하나 빼기(삭제·숨김). 마지막 하나면 둘 다 0. rating_sum 을 먼저 둔다 — MySQL 은 SET 을 왼쪽부터 적용해
     * 뒤 식이 앞에서 바뀐 값을 보므로, review_count 를 읽는 식이 바뀌기 전 값을 보게 한다.
     */
    @Modifying(flushAutomatically = true)
    @Transactional(transactionManager = "rwTransactionManager")
    @Query(
        value =
            "update products set rating_sum = case when review_count <= 1 then 0 else rating_sum - :rating end, " +
                "review_count = case when review_count <= 1 then 0 else review_count - 1 end where id = :id",
        nativeQuery = true,
    )
    fun removeRating(
        @Param("id") id: Long,
        @Param("rating") rating: Long,
    ): Int

    /** 노출 리뷰의 별점 수정. */
    @Modifying(flushAutomatically = true)
    @Transactional(transactionManager = "rwTransactionManager")
    @Query(value = "update products set rating_sum = rating_sum + :delta where id = :id", nativeQuery = true)
    fun addRatingDelta(
        @Param("id") id: Long,
        @Param("delta") delta: Long,
    ): Int
}
