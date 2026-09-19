package com.example.commerce.api.category

import com.example.commerce.api.category.response.CategoryResponse
import com.example.commerce.application.usecase.category.GetCategoriesUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val getCategoriesUseCase: GetCategoriesUseCase,
) {
    @GetMapping
    fun categories(): ResponseEntity<List<CategoryResponse>> = ResponseEntity.ok(getCategoriesUseCase.execute().map(CategoryResponse::from))
}
