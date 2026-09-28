package com.example.commerce.api.category

import com.example.commerce.api.category.response.CategoryResponse
import com.example.commerce.application.usecase.category.GetCategoriesUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "카테고리 (앱)",
    description = "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). 모두 계정 토큰(aud modu-commerce) 필요. 둘러보기라 커머스 가입 전에도 부를 수 있다.",
)
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val getCategoriesUseCase: GetCategoriesUseCase,
) {
    @Operation(
        summary = "카테고리 트리 조회",
        description = "대분류 아래 소분류(children)를 붙인 트리를 정렬 순서(sortOrder, id)대로 돌려준다. 아이콘(이모지)·색도 함께 온다.",
    )
    @GetMapping
    fun categories(): ResponseEntity<List<CategoryResponse>> = ResponseEntity.ok(getCategoriesUseCase.execute().map(CategoryResponse::from))
}
