package com.example.commerce.api.category

import com.example.commerce.api.category.request.CategoryRequest
import com.example.commerce.api.category.response.CategoryNodeResponse
import com.example.commerce.api.category.response.CategoryResponse
import com.example.commerce.application.usecase.category.CreateCategoryUseCase
import com.example.commerce.application.usecase.category.DeleteCategoryUseCase
import com.example.commerce.application.usecase.category.GetAdminCategoriesUseCase
import com.example.commerce.application.usecase.category.UpdateCategoryUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api-admin/v1/categories")
class AdminCategoryController(
    private val getAdminCategoriesUseCase: GetAdminCategoriesUseCase,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
) {
    @GetMapping
    fun categories(): ResponseEntity<List<CategoryResponse>> =
        ResponseEntity.ok(getAdminCategoriesUseCase.execute().map(CategoryResponse::from))

    @PostMapping
    fun create(
        @Valid @RequestBody request: CategoryRequest,
    ): ResponseEntity<CategoryNodeResponse> {
        val created = createCategoryUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/api-admin/v1/categories/${created.id}")).body(CategoryNodeResponse.from(created))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: CategoryRequest,
    ): ResponseEntity<CategoryNodeResponse> =
        ResponseEntity.ok(CategoryNodeResponse.from(updateCategoryUseCase.execute(id, request.toCommand())))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteCategoryUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
