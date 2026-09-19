package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductSort
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.repository.ro.ProductRoRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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

    /** 앱 목록. [categoryIds] 가 null 이면 전체. */
    fun findAppPage(
        categoryIds: Collection<Long>?,
        keyword: String?,
        sort: ProductSort,
        page: Int,
        size: Int,
    ): Page<Product> = productRoRepository.searchAppPage(categoryIds, keyword, sort, pageOf(page, size))

    /** 앱 상세. 숨긴 상품은 없는 것으로 본다. */
    fun findSellingProduct(id: Long): Product =
        findProduct(id).takeIf { it.status == ProductStatus.SELLING }
            ?: throw EntityNotFoundException("id: $id 에 해당하는 상품이 없습니다.")

    fun findProduct(id: Long): Product =
        productRoRepository.findById(id)
            ?: run {
                logger.error { "Product not found: $id" }
                throw EntityNotFoundException("id: $id 에 해당하는 상품이 없습니다.")
            }

    /** page 는 0 이상, size 는 1~100 으로 자른다. 잘못된 쿼리 파라미터로 전체 테이블을 긁지 않게. */
    fun searchAdminProducts(
        keyword: String?,
        page: Int,
        size: Int,
    ): Page<Product> = searchAdminProducts(keyword, null, null, page, size)

    fun searchAdminProducts(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        page: Int,
        size: Int,
    ): Page<Product> = productRoRepository.searchAdminPage(keyword, categoryId, status, pageOf(page, size))

    private fun pageOf(
        page: Int,
        size: Int,
    ) = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
