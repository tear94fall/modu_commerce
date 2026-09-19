package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/** 상품 사진 한 장. 순서 0 이 대표 사진이다. */
@Entity
@Table(name = "product_images")
class ProductImage(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    @Column(name = "url", nullable = false, length = 500)
    var url: String,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : IdentityEntity()
