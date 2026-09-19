package com.example.commerce.api.category.request

import com.example.commerce.application.usecase.command.CategoryCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CategoryRequest(
    @field:NotBlank(message = "카테고리 이름을 입력해 주세요.")
    @field:Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
    val name: String? = null,
    val parentId: Long? = null,
    val sortOrder: Int? = null,
) {
    fun toCommand() = CategoryCommand(name = requireNotNull(name).trim(), parentId = parentId, sortOrder = sortOrder ?: 0)
}
