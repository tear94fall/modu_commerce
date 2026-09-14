package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductQueryService
import com.example.commerce.application.usecase.result.ProductPageResult
import org.springframework.stereotype.Component

@Component
class SearchAdminProductsUseCase(
    private val productQueryService: ProductQueryService,
) {
    fun execute(
        keyword: String?,
        page: Int,
        size: Int,
    ): ProductPageResult = ProductPageResult.from(productQueryService.searchAdminProducts(keyword, page, size))
}
