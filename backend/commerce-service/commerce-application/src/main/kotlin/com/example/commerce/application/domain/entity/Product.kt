package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 삭제는 deleted_at 만 채운다(소프트 삭제). @SQLRestriction 이 조회·검색·단건 어디서든
 * 삭제된 행을 빼므로 쿼리마다 조건을 넣지 않는다. 삭제 행까지 세야 하면 네이티브 쿼리를 쓴다.
 */
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
class Product(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @Column(name = "description", nullable = false, length = 500)
    var description: String,
    /** 원 단위 가격 */
    @Column(name = "price", nullable = false)
    var price: Long,
    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,
) : BaseEntity() {
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    fun update(
        name: String,
        description: String,
        price: Long,
        imageUrl: String?,
    ) {
        this.name = name
        this.description = description
        this.price = price
        this.imageUrl = imageUrl
    }

    fun delete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }

    fun isDeleted(): Boolean = deletedAt != null

    override fun toString(): String = "Product(id=$id, name='$name', price=$price)"

    companion object {
        fun create(
            name: String,
            description: String,
            price: Long,
            imageUrl: String? = null,
        ): Product =
            Product(
                name = name,
                description = description,
                price = price,
                imageUrl = imageUrl,
            )
    }
}
