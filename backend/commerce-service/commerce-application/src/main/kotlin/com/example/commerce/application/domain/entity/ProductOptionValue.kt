package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/** 옵션 값(블랙, M…). */
@Entity
@Table(name = "product_option_values")
class ProductOptionValue(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: ProductOptionGroup,
    @Column(name = "name", nullable = false, length = 30)
    var name: String,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : IdentityEntity()
