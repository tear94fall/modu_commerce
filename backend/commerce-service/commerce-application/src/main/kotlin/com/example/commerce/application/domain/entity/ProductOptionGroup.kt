package com.example.commerce.application.domain.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table

/** 옵션 그룹(색상, 사이즈…). 값들을 소유한다. */
@Entity
@Table(name = "product_option_groups")
class ProductOptionGroup(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    @Column(name = "name", nullable = false, length = 30)
    var name: String,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : IdentityEntity() {
    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    val values: MutableList<ProductOptionValue> = mutableListOf()

    fun addValue(
        name: String,
        sortOrder: Int,
    ): ProductOptionValue = ProductOptionValue(group = this, name = name, sortOrder = sortOrder).also { values.add(it) }
}
