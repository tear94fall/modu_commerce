package com.example.commerce.api.review

import com.example.commerce.application.domain.entity.Review
import com.example.commerce.application.usecase.command.EditReviewCommand
import com.example.commerce.application.usecase.command.WriteReviewCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "리뷰 쓰기 요청. 주문 줄 하나에 리뷰 하나")
data class WriteReviewRequest(
    @field:Schema(description = "리뷰할 주문 줄(주문 상품) id. 필수, 내 주문이어야 한다", example = "20")
    @field:NotNull(message = "어느 주문 상품의 리뷰인지 알려 주세요.")
    val orderItemId: Long? = null,
    @field:Schema(description = "별점. 필수, 1~5", example = "5")
    @field:NotNull(message = "별점을 골라 주세요.")
    @field:Min(value = 1, message = "별점은 1~5 사이여야 합니다.")
    @field:Max(value = 5, message = "별점은 1~5 사이여야 합니다.")
    val rating: Int? = null,
    @field:NotBlank(message = "리뷰 내용을 써 주세요.")
    @field:Size(min = Review.MIN_CONTENT, max = Review.MAX_CONTENT, message = "리뷰는 ${Review.MIN_CONTENT}~${Review.MAX_CONTENT}자로 써 주세요.")
    @field:Schema(description = "리뷰 내용. 필수, 앞뒤 공백을 뺀 10~1000자", example = "배송이 빠르고 포장도 꼼꼼했어요. 잘 쓰겠습니다.")
    val content: String? = null,
) {
    fun toCommand() = WriteReviewCommand(requireNotNull(orderItemId), requireNotNull(rating), requireNotNull(content))
}

@Schema(description = "리뷰 수정 요청. 별점과 내용을 통째로 바꾼다")
data class EditReviewRequest(
    @field:Schema(description = "별점. 필수, 1~5", example = "4")
    @field:NotNull(message = "별점을 골라 주세요.")
    @field:Min(value = 1, message = "별점은 1~5 사이여야 합니다.")
    @field:Max(value = 5, message = "별점은 1~5 사이여야 합니다.")
    val rating: Int? = null,
    @field:NotBlank(message = "리뷰 내용을 써 주세요.")
    @field:Size(min = Review.MIN_CONTENT, max = Review.MAX_CONTENT, message = "리뷰는 ${Review.MIN_CONTENT}~${Review.MAX_CONTENT}자로 써 주세요.")
    @field:Schema(description = "리뷰 내용. 필수, 앞뒤 공백을 뺀 10~1000자", example = "한 달 써 보니 뚜껑이 조금 헐거워요.")
    val content: String? = null,
) {
    fun toCommand() = EditReviewCommand(requireNotNull(rating), requireNotNull(content))
}

@Schema(description = "리뷰 숨김·노출 본문.")
data class SetHiddenRequest(
    @field:Schema(description = "true 면 숨기고 false 면 다시 보인다. 필수", example = "true")
    @field:NotNull(message = "숨김 여부를 알려 주세요.")
    val hidden: Boolean? = null,
    @field:Schema(description = "숨김 사유(관리용 메모). 선택, 200자 이하. 숨길 때만 쓴다", example = "욕설 포함")
    @field:Size(max = 200, message = "사유는 200자까지입니다.")
    val reason: String? = null,
)
