package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/** 최대 [MAX_DEPTH]단계 카테고리(대 > 중 > 소). 깊이 제한은 서비스가 본다. 소프트 삭제. */
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

    /** 형제 사이 순서만 바꾼다. */
    fun reorder(sortOrder: Int) {
        this.sortOrder = sortOrder
    }

    fun delete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }

    fun isDeleted(): Boolean = deletedAt != null

    fun isRoot(): Boolean = parent == null

    /** 최상위부터 자기까지(길이 1..[MAX_DEPTH]). */
    fun path(): List<Category> = generateSequence(this) { it.parent }.toList().asReversed()

    /** 최상위가 1. */
    fun depth(): Int = generateSequence(this) { it.parent }.count()

    /** 자기 또는 조상 중에 [id] 가 있는가. */
    fun isSelfOrDescendantOf(id: Long): Boolean = generateSequence(this) { it.parent }.any { it.id == id }

    /** "문구 > 노트·데스크 > 노트". */
    fun pathName(): String = path().joinToString(PATH_SEPARATOR) { it.name }

    override fun toString(): String = "Category(id=$id, name='$name')"

    companion object {
        /** 최상위가 1단계. */
        const val MAX_DEPTH = 3
        const val PATH_SEPARATOR = " > "

        private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")

        fun create(
            name: String,
            parent: Category? = null,
            sortOrder: Int = 0,
        ): Category = Category(name = name, parent = parent, sortOrder = sortOrder)
    }
}
