package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/** 2단계 카테고리(상위 > 하위). 깊이 제한은 서비스가 본다. 소프트 삭제. */
@Entity
@Table(name = "categories")
@SQLRestriction("deleted_at IS NULL")
class Category(
    @Column(name = "name", nullable = false, length = 50)
    var name: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Category? = null,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
) : BaseEntity() {
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    /** 앱 카테고리 화면의 아이콘(이모지 한 개). 없으면 앱이 이름 첫 글자를 보여 준다. */
    @Column(name = "icon", length = 16)
    var icon: String? = null
        protected set

    /** 아이콘 타일 배경색 #RRGGBB. 없으면 앱 기본색. */
    @Column(name = "color", length = 7)
    var color: String? = null
        protected set

    fun decorate(
        icon: String?,
        color: String?,
    ): Category {
        require(icon == null || icon.length <= 16) { "아이콘은 이모지 한 개로 넣어 주세요." }
        require(color == null || COLOR.matches(color)) { "아이콘 색은 #RRGGBB 형식으로 입력하세요." }
        this.icon = icon
        this.color = color?.uppercase()
        return this
    }

    fun update(
        name: String,
        parent: Category?,
        sortOrder: Int,
    ) {
        this.name = name
        this.parent = parent
        this.sortOrder = sortOrder
    }

    fun delete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }

    fun isDeleted(): Boolean = deletedAt != null

    fun isRoot(): Boolean = parent == null

    /** 상위부터 자기까지. 2단계라 길이는 1 또는 2 다. */
    fun path(): List<Category> = listOfNotNull(parent, this)

    override fun toString(): String = "Category(id=$id, name='$name')"

    companion object {
        private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")

        fun create(
            name: String,
            parent: Category? = null,
            sortOrder: Int = 0,
        ): Category = Category(name = name, parent = parent, sortOrder = sortOrder)
    }
}
