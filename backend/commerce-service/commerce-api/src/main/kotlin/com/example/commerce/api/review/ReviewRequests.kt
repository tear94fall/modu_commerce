package com.example.commerce.api.review

import com.example.commerce.application.domain.entity.Review
import com.example.commerce.application.usecase.command.EditReviewCommand
import com.example.commerce.application.usecase.command.WriteReviewCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class WriteReviewRequest(
    @field:NotNull(message = "어느 주문 상품의 리뷰인지 알려 주세요.")
    val orderItemId: Long? = null,
    @field:NotNull(message = "별점을 골라 주세요.")
    @field:Min(value = 1, message = "별점은 1~5 사이여야 합니다.")
    @field:Max(value = 5, message = "별점은 1~5 사이여야 합니다.")
    val rating: Int? = null,
    @field:NotBlank(message = "리뷰 내용을 써 주세요.")
    @field:Size(min = Review.MIN_CONTENT, max = Review.MAX_CONTENT, message = "리뷰는 ${Review.MIN_CONTENT}~${Review.MAX_CONTENT}자로 써 주세요.")
    val content: String? = null,
) {
    fun toCommand() = WriteReviewCommand(requireNotNull(orderItemId), requireNotNull(rating), requireNotNull(content))
}

data class EditReviewRequest(
    @field:NotNull(message = "별점을 골라 주세요.")
    @field:Min(value = 1, message = "별점은 1~5 사이여야 합니다.")
    @field:Max(value = 5, message = "별점은 1~5 사이여야 합니다.")
    val rating: Int? = null,
    @field:NotBlank(message = "리뷰 내용을 써 주세요.")
    @field:Size(min = Review.MIN_CONTENT, max = Review.MAX_CONTENT, message = "리뷰는 ${Review.MIN_CONTENT}~${Review.MAX_CONTENT}자로 써 주세요.")
    val content: String? = null,
) {
    fun toCommand() = EditReviewCommand(requireNotNull(rating), requireNotNull(content))
}

data class SetHiddenRequest(
    @field:NotNull(message = "숨김 여부를 알려 주세요.")
    val hidden: Boolean? = null,
    @field:Size(max = 200, message = "사유는 200자까지입니다.")
    val reason: String? = null,
)
