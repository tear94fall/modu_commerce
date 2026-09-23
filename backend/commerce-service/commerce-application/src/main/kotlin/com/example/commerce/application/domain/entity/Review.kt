package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 상품 리뷰. 구매한 주문 줄(order_items) 하나에 리뷰 하나다(유니크). 상품·주문이 나중에 바뀌어도
 * 목록이 그대로이도록 상품명·옵션·사진과 작성자 이름·이메일(member-service 조회값)을 복사해 둔다.
 *
 * 삭제는 deleted_at 만 채운다(소프트 삭제, 상품과 같은 방식). 관리자 숨김(hidden)은 앱 목록·평점 집계에서만 빠지고
 * 작성자 본인과 백오피스에는 보인다.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = [UniqueConstraint(name = "uk_reviews_order_item", columnNames = ["order_item_id"])],
    indexes = [
        Index(
            name = "ix_reviews_product",
            columnList = "product_id,hidden,deleted_at",
        ), Index(name = "ix_reviews_user", columnList = "user_id"),
    ],
)
@SQLRestriction("deleted_at IS NULL")
class Review(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    val orderItem: OrderItem,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    /** member-service 에서 받은 이름. 조회가 안 되면 null(앱은 '모두 회원'으로 보여 준다). */
    @Column(name = "author_name", length = 100)
    val authorName: String?,
    @Column(name = "author_email", length = 200)
    val authorEmail: String?,
    @Column(name = "product_name", nullable = false, length = 100)
    val productName: String,
    @Column(name = "option_label", nullable = false, length = 200)
    val optionLabel: String,
    @Column(name = "product_image_url", length = 500)
    val productImageUrl: String?,
    rating: Int,
    content: String,
) : BaseEntity() {
    @Column(name = "rating", nullable = false)
    var rating: Int = validRating(rating)
        protected set

    @Column(name = "content", nullable = false, length = 1000)
    var content: String = validContent(content)
        protected set

    @Column(name = "hidden", nullable = false, columnDefinition = "bit not null default 0")
    var hidden: Boolean = false
        protected set

    /** 관리자가 숨길 때 남기는 사유. 작성자에게 보여 준다. */
    @Column(name = "hidden_reason", length = 200)
    var hiddenReason: String? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    fun edit(
        rating: Int,
        content: String,
    ) {
        this.rating = validRating(rating)
        this.content = validContent(content)
    }

    fun hide(reason: String?) {
        hidden = true
        hiddenReason = reason?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun unhide() {
        hidden = false
        hiddenReason = null
    }

    fun delete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }

    fun isDeleted(): Boolean = deletedAt != null

    /** 앱 목록·평점 집계에 들어가는가. 숨김 리뷰는 빠진다. */
    fun isVisible(): Boolean = !hidden && !isDeleted()

    companion object {
        const val MIN_CONTENT = 10
        const val MAX_CONTENT = 1000

        fun validRating(rating: Int): Int {
            require(rating in 1..5) { "별점은 1~5 사이여야 합니다." }
            return rating
        }

        fun validContent(content: String): String {
            val trimmed = content.trim()
            require(trimmed.length >= MIN_CONTENT) { "리뷰는 ${MIN_CONTENT}자 이상 써 주세요." }
            require(trimmed.length <= MAX_CONTENT) { "리뷰는 ${MAX_CONTENT}자까지 쓸 수 있습니다." }
            return trimmed
        }

        /** 구매 확정 전이라도 결제된 주문이면 리뷰를 쓸 수 있다(모의 결제). 취소 주문은 안 된다. */
        fun canReview(order: Order): Boolean = !order.isCancelled()

        /** 앱에 보이는 작성자 이름. 가운데를 가린다: 임준섭 → 임*섭, 김철 → 김*, 홍길동님 → 홍**님. 이름이 없으면 '모두 회원'. */
        fun maskName(name: String?): String {
            val n = name?.trim().orEmpty()
            if (n.isEmpty()) return "모두 회원"
            if (n.length == 1) return "*"
            if (n.length == 2) return n.substring(0, 1) + "*"
            return n.first() + "*".repeat(n.length - 2) + n.last()
        }
    }
}
