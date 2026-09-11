package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity : IdentityEntity() {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set
}

@MappedSuperclass
abstract class IdentityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun isPersisted(): Boolean = id != null

    /**
     * 영속화된 엔티티는 id 로, 영속화 전 엔티티는 참조 동일성으로 비교한다.
     * Hibernate 프록시는 실제 엔티티 클래스로 풀어서 비교한다.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentityEntity) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        val id = this.id ?: return false
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: System.identityHashCode(this)
}
