package com.example.commerce.application.usecase.product

import com.example.commerce.application.domain.entity.ProductSort
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.service.CategoryQueryService
import com.example.commerce.application.service.ProductCommandService
import com.example.commerce.application.service.ProductQueryService
import com.example.commerce.application.service.WishlistQueryService
import com.example.commerce.application.usecase.command.ProductCommand
import com.example.commerce.application.usecase.result.AdminProductSummaryResult
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.ProductDetailResult
import com.example.commerce.application.usecase.result.ProductSummaryResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 앱 목록. 컬렉션(SKU 재고)을 읽어 품절을 계산하므로 RO 트랜잭션 안에서 매핑한다. */
@Component
class GetProductsUseCase(
    private val productQueryService: ProductQueryService,
    private val categoryQueryService: CategoryQueryService,
    private val wishlistQueryService: WishlistQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        categoryId: Long?,
        keyword: String?,
        sort: String?,
        page: Int,
        size: Int,
    ): PageResult<ProductSummaryResult> {
        val categoryIds = categoryId?.let { categoryQueryService.subtreeIds(it) }
        val products = productQueryService.findAppPage(categoryIds, keyword, ProductSort.fromParam(sort), page, size)
        val wished = wishlistQueryService.wishedIds(userId, products.content.mapNotNull { it.id })
        return PageResult.from(products) { ProductSummaryResult.from(it, wished = it.id in wished) }
    }
}

@Component
class GetProductDetailUseCase(
    private val productQueryService: ProductQueryService,
    private val wishlistQueryService: WishlistQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        id: Long,
    ): ProductDetailResult {
        val product = productQueryService.findSellingProduct(id)
        return ProductDetailResult.from(product, wished = wishlistQueryService.isWished(userId, id))
    }
}

@Component
class GetAdminProductUseCase(
    private val productQueryService: ProductQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(id: Long): ProductDetailResult = ProductDetailResult.from(productQueryService.findProduct(id))
}

@Component
class SearchAdminProductsUseCase(
    private val productQueryService: ProductQueryService,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        page: Int,
        size: Int,
    ): PageResult<AdminProductSummaryResult> =
        PageResult.from(productQueryService.searchAdminProducts(keyword, categoryId, status, page, size), AdminProductSummaryResult::from)
}

@Component
class CreateProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(command: ProductCommand): ProductDetailResult = ProductDetailResult.from(productCommandService.create(command))
}

@Component
class UpdateProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    @Transactional(transactionManager = "rwTransactionManager")
    fun execute(
        id: Long,
        command: ProductCommand,
    ): ProductDetailResult = ProductDetailResult.from(productCommandService.update(id, command))
}

@Component
class DeleteProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    fun execute(id: Long) = productCommandService.delete(id)
}
