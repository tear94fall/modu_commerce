package com.example.commerce.api.category

import com.example.commerce.api.category.request.CategoryRequest
import com.example.commerce.api.category.response.CategoryNodeResponse
import com.example.commerce.api.category.response.CategoryResponse
import com.example.commerce.application.usecase.category.CreateCategoryUseCase
import com.example.commerce.application.usecase.category.DeleteCategoryUseCase
import com.example.commerce.application.usecase.category.GetAdminCategoriesUseCase
import com.example.commerce.application.usecase.category.UpdateCategoryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(
    name = "카테고리 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/categories")
class AdminCategoryController(
    private val getAdminCategoriesUseCase: GetAdminCategoriesUseCase,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
) {
    @Operation(
        summary = "카테고리 트리 조회",
        description =
            "대분류 아래 소분류(children)를 붙인 트리를 정렬 순서(sortOrder, id)대로 돌려준다. " +
                "카테고리마다 상품 수를 master 에서 세어 붙인다(방금 등록한 상품도 바로 센다).",
    )
    @GetMapping
    fun categories(): ResponseEntity<List<CategoryResponse>> =
        ResponseEntity.ok(getAdminCategoriesUseCase.execute().map(CategoryResponse::from))

    @Operation(
        summary = "카테고리 등록",
        description =
            "parentId 가 없으면 대분류, 있으면 그 아래 소분류로 만든다(2단계까지). 201 과 만든 노드를 돌려준다. " +
                "없는 부모면 404, 부모가 이미 소분류거나 아이콘·색 형식이 틀리면 400.",
    )
    @PostMapping
    fun create(
        @Valid @RequestBody request: CategoryRequest,
    ): ResponseEntity<CategoryNodeResponse> {
        val created = createCategoryUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/api-admin/v1/categories/${created.id}")).body(CategoryNodeResponse.from(created))
    }

    @Operation(
        summary = "카테고리 수정",
        description =
            "이름·부모·정렬 순서·아이콘·색을 통째로 바꾼다(빠진 아이콘·색은 지운다). 없는 카테고리면 404. " +
                "자기 자신 아래로 옮기거나, 하위 카테고리가 있는 대분류를 다른 카테고리 아래로 옮기면 400.",
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "카테고리 id", example = "3")
        @PathVariable id: Long,
        @Valid @RequestBody request: CategoryRequest,
    ): ResponseEntity<CategoryNodeResponse> =
        ResponseEntity.ok(CategoryNodeResponse.from(updateCategoryUseCase.execute(id, request.toCommand())))

    @Operation(
        summary = "카테고리 삭제",
        description = "소프트 삭제하고 204 를 돌려준다. 없는 카테고리면 404, 하위 카테고리나 상품이 남아 있으면 400.",
    )
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "카테고리 id", example = "3")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteCategoryUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
