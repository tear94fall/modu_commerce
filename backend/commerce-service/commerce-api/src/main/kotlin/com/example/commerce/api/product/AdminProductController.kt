package com.example.commerce.api.product

import com.example.commerce.api.product.request.ProductRequest
import com.example.commerce.api.product.response.GetProductResponse
import com.example.commerce.api.product.response.ProductPageResponse
import com.example.commerce.application.usecase.CreateProductUseCase
import com.example.commerce.application.usecase.DeleteProductUseCase
import com.example.commerce.application.usecase.GetProductUseCase
import com.example.commerce.application.usecase.SearchAdminProductsUseCase
import com.example.commerce.application.usecase.UpdateProductUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 백오피스가 게이트웨이(/commerce-service/api-admin 이하)를 거쳐 부른다. 권한은 SecurityConfig 의 admin 체인이 본다. */
@RestController
@RequestMapping("/api-admin/v1/products")
class AdminProductController(
    private val searchAdminProductsUseCase: SearchAdminProductsUseCase,
    private val getProductUseCase: GetProductUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
) {
    @GetMapping
    fun products(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<ProductPageResponse> = ResponseEntity.ok(ProductPageResponse.from(searchAdminProductsUseCase.execute(q, page, size)))

    @GetMapping("/{id}")
    fun product(
        @PathVariable id: Long,
    ): ResponseEntity<GetProductResponse> = ResponseEntity.ok(GetProductResponse.from(getProductUseCase.execute(id)))

    @PostMapping
    fun create(
        @Valid @RequestBody request: ProductRequest,
    ): ResponseEntity<GetProductResponse> {
        val created = createProductUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/api-admin/v1/products/${created.id}")).body(GetProductResponse.from(created))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: ProductRequest,
    ): ResponseEntity<GetProductResponse> =
        ResponseEntity.ok(GetProductResponse.from(updateProductUseCase.execute(id, request.toCommand())))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteProductUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
