package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductQueryService
import com.example.commerce.application.usecase.result.GetProductResult
import org.springframework.stereotype.Component

@Component
class GetProductUseCase(
    private val productQueryService: ProductQueryService,
) {
    fun execute(id: Long): GetProductResult = GetProductResult.from(productQueryService.findProduct(id))
}
