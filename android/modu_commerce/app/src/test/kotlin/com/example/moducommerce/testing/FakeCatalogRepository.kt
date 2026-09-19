package com.example.moducommerce.testing

import com.example.moducommerce.core.model.Category
import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.model.ProductDetail
import com.example.moducommerce.core.model.ProductSort
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.data.repository.CatalogRepository

fun product(id: Long, wished: Boolean = false, price: Long = 10_000) =
    ProductSummary(id = id, name = "상품 $id", imageUrl = null, price = price, listPrice = null, discountRate = 0, soldOut = false, wished = wished)

class FakeCatalogRepository : CatalogRepository {
    /** 페이지 번호 → 결과. 없는 페이지는 빈 페이지. */
    val pages = mutableMapOf<Int, Page<ProductSummary>>()
    val wishCalls = mutableListOf<Pair<Long, Boolean>>()
    var wishResult: Result<Unit> = Result.success(Unit)
    var detailResult: Result<ProductDetail> = Result.failure(IllegalStateException("no detail"))
    var categoriesResult: Result<List<Category>> = Result.success(emptyList())
    val requests = mutableListOf<Triple<Long?, String?, ProductSort>>()
    var fail = false

    override suspend fun categories(): Result<List<Category>> = categoriesResult

    override suspend fun products(categoryId: Long?, query: String?, sort: ProductSort, page: Int, size: Int): Result<Page<ProductSummary>> {
        requests += Triple(categoryId, query, sort)
        if (fail) return Result.failure(RuntimeException("boom"))
        return Result.success(pages[page] ?: Page(emptyList(), 0, 0, page))
    }

    override suspend fun product(id: Long): Result<ProductDetail> = detailResult

    override suspend fun wishlist(page: Int, size: Int): Result<Page<ProductSummary>> = products(null, null, ProductSort.LATEST, page, size)

    override suspend fun setWished(productId: Long, wished: Boolean): Result<Unit> {
        wishCalls += productId to wished
        return wishResult
    }
}
