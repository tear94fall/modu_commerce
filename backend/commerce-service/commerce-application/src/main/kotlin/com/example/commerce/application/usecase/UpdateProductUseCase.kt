package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductCommandService
import com.example.commerce.application.usecase.command.ProductCommand
import com.example.commerce.application.usecase.result.GetProductResult
import org.springframework.stereotype.Component

@Component
class UpdateProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    fun execute(
        id: Long,
        command: ProductCommand,
    ): GetProductResult = GetProductResult.from(productCommandService.update(id, command))
}
