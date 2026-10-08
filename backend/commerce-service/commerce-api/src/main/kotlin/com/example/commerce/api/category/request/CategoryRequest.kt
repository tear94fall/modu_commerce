package com.example.commerce.api.category.request

import com.example.commerce.application.usecase.command.CategoryCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "카테고리 등록·수정 본문. 수정도 모든 값을 다시 보낸다.")
data class CategoryRequest(
    @field:Schema(description = "카테고리 이름. 필수, 50자 이하(앞뒤 공백은 걷는다)", example = "문구")
    @field:NotBlank(message = "카테고리 이름을 입력해 주세요.")
    @field:Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
    val name: String? = null,
    @field:Schema(description = "부모 카테고리 id. 없으면 최상위가 된다. 3단계 카테고리 아래에는 둘 수 없다", example = "1")
    val parentId: Long? = null,
    @field:Schema(
        description = "정렬 순서(작을수록 앞). 없으면 등록은 형제 맨 뒤, 수정은 그대로(다른 부모로 옮기면 맨 뒤)",
        example = "0",
    )
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
            sortOrder = sortOrder,
            icon = icon?.trim()?.takeIf { it.isNotEmpty() },
            color = color?.trim()?.takeIf { it.isNotEmpty() },
        )
}

@Schema(description = "형제 순서 바꾸기 본문. ids 는 그 부모의 지금 하위 카테고리 전부를 원하는 순서로.")
data class CategoryOrderRequest(
    @field:Schema(description = "부모 카테고리 id. null 이면 최상위 카테고리들의 순서", example = "1")
    val parentId: Long? = null,
    @field:Schema(description = "새 순서대로 나열한 하위 카테고리 id(빠짐·중복 없이). 앞에서부터 sortOrder 0, 1, 2…", example = "[5, 3, 4]")
    @field:NotNull(message = "순서를 바꿀 카테고리 id 목록을 보내 주세요.")
    val ids: List<Long>? = null,
)
