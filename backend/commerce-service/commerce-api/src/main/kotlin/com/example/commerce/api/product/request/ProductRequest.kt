package com.example.commerce.api.product.request

import com.example.commerce.application.usecase.command.ProductCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

/** 백오피스 등록·수정 본문. 빠진 필드도 400 메시지로 알려 주려고 전부 nullable 로 받는다. */
data class ProductRequest(
    @field:NotBlank(message = "상품 이름을 입력해 주세요.")
    @field:Size(max = 100, message = "상품 이름은 100자 이하여야 합니다.")
    val name: String? = null,
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    val description: String? = null,
    @field:NotNull(message = "가격을 입력해 주세요.")
    @field:PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
    val price: Long? = null,
    @field:Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
    @field:Pattern(regexp = "^(https?://\\S+)?$", message = "이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.")
    val imageUrl: String? = null,
) {
    /** @Valid 를 통과한 뒤에만 부른다. */
    fun toCommand(): ProductCommand =
        ProductCommand(
            name = requireNotNull(name).trim(),
            description = description?.trim().orEmpty(),
            price = requireNotNull(price),
            imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
}
