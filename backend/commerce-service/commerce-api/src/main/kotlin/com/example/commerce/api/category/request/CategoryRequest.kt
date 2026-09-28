package com.example.commerce.api.category.request

import com.example.commerce.application.usecase.command.CategoryCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "카테고리 등록·수정 본문. 수정도 모든 값을 다시 보낸다.")
data class CategoryRequest(
    @field:Schema(description = "카테고리 이름. 필수, 50자 이하(앞뒤 공백은 걷는다)", example = "문구")
    @field:NotBlank(message = "카테고리 이름을 입력해 주세요.")
    @field:Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
    val name: String? = null,
    @field:Schema(description = "부모(대분류) 카테고리 id. 없으면 대분류가 된다. 소분류 아래에는 둘 수 없다", example = "1")
    val parentId: Long? = null,
    @field:Schema(description = "정렬 순서(작을수록 앞). 없으면 0", example = "0")
    val sortOrder: Int? = null,
    /** 이모지 한 개(빈 값이면 없음). */
    @field:Schema(description = "아이콘 이모지 한 개(16자 이하). 빈 값이면 없음", example = "✏️")
    val icon: String? = null,
    /** #RRGGBB(빈 값이면 없음). */
    @field:Schema(description = "아이콘 배경색 #RRGGBB(대문자로 저장). 빈 값이면 없음", example = "#FDE68A")
    val color: String? = null,
) {
    fun toCommand() =
        CategoryCommand(
            name = requireNotNull(name).trim(),
            parentId = parentId,
            sortOrder = sortOrder ?: 0,
            icon = icon?.trim()?.takeIf { it.isNotEmpty() },
            color = color?.trim()?.takeIf { it.isNotEmpty() },
        )
}
