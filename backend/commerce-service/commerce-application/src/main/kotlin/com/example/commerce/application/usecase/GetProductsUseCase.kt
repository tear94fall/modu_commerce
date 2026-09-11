package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductQueryService
import com.example.commerce.application.usecase.result.GetProductResult
import org.springframework.stereotype.Component

@Component
class GetProductsUseCase(
    private val productQueryService: ProductQueryService,
) {
    fun execute(keyword: String?): List<GetProductResult> = productQueryService.findProducts(keyword).map(GetProductResult::from)
}
