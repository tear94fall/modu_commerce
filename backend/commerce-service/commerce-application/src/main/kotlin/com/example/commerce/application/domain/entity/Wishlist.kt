package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** 찜. userId 는 모두 계정 토큰의 sub(회원 id 문자열)다. */
@Entity
@Table(
    name = "wishlists",
    uniqueConstraints = [UniqueConstraint(name = "uk_wishlists_user_product", columnNames = ["user_id", "product_id"])],
)
class Wishlist(
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
) : BaseEntity()
