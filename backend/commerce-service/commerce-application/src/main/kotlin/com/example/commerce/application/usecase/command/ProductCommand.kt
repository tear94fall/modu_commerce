package com.example.commerce.application.usecase.command

import com.example.commerce.application.domain.entity.OptionGroupSpec
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.SkuSpec

/** 등록·수정 입력. 형식 검증(길이·범위·URL)은 api 모듈의 요청 DTO 가 끝낸 값이다. 조합 규칙은 엔티티가 본다. */
data class ProductCommand(
    val name: String,
    val description: String,
    val detail: String?,
    val price: Long,
    val listPrice: Long?,
    val categoryId: Long?,
    val status: ProductStatus,
    val images: List<String>,
    val optionGroups: List<OptionGroupSpec>,
    val skus: List<SkuSpec>,
)

data class CategoryCommand(
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
    /** 이모지 한 개. 없으면 null. */
    val icon: String? = null,
    /** #RRGGBB. 없으면 null. */
    val color: String? = null,
)
