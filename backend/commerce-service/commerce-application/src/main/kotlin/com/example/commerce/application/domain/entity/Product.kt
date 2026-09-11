package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "products")
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
