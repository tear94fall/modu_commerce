package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.ro.ProductRoRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class ProductQueryService(
    private val productRoRepository: ProductRoRepository,
) {
    fun findProducts(keyword: String?): List<Product> =
        if (keyword.isNullOrBlank()) {
            productRoRepository.findAllByOrderByIdAsc()
        } else {
            productRoRepository.search(keyword.trim())
        }

    fun findProduct(id: Long): Product =
        productRoRepository.findById(id)
            ?: run {
                logger.error { "Product not found: $id" }
                throw EntityNotFoundException("id: $id 에 해당하는 상품이 없습니다.")
            }
}
