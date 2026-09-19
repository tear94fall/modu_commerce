package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** 배송지. 사용자당 여러 개, 기본은 하나. */
@Entity
@Table(name = "addresses")
class Address(
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "recipient", nullable = false, length = 30)
    var recipient: String,
    @Column(name = "phone", nullable = false, length = 20)
    var phone: String,
    @Column(name = "zip_code", nullable = false, length = 5)
    var zipCode: String,
    @Column(name = "address1", nullable = false, length = 100)
    var address1: String,
    @Column(name = "address2", length = 100)
    var address2: String? = null,
    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
) : BaseEntity() {
    fun update(
        recipient: String,
        phone: String,
        zipCode: String,
        address1: String,
        address2: String?,
    ) {
        this.recipient = recipient
        this.phone = phone
        this.zipCode = zipCode
        this.address1 = address1
        this.address2 = address2
    }

    fun fullAddress(): String = listOfNotNull(address1, address2?.takeIf { it.isNotBlank() }).joinToString(" ")
}
