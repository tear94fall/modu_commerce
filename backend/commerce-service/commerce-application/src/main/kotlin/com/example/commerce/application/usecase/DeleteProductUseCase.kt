package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductCommandService
import org.springframework.stereotype.Component

@Component
class DeleteProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    fun execute(id: Long) = productCommandService.delete(id)
}
