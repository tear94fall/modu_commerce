package com.example.commerce.api.product

import com.example.commerce.api.product.response.GetProductResponse
import com.example.commerce.application.usecase.GetProductUseCase
import com.example.commerce.application.usecase.GetProductsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val getProductsUseCase: GetProductsUseCase,
    private val getProductUseCase: GetProductUseCase,
) {
    @GetMapping
    fun products(
        @RequestParam(required = false) q: String?,
    ): ResponseEntity<List<GetProductResponse>> = ResponseEntity.ok(getProductsUseCase.execute(q).map(GetProductResponse::from))

    @GetMapping("/{id}")
    fun product(
        @PathVariable id: Long,
    ): ResponseEntity<GetProductResponse> = ResponseEntity.ok(GetProductResponse.from(getProductUseCase.execute(id)))
}
