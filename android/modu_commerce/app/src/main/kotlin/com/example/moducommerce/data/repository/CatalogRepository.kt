package com.example.moducommerce.data.repository

import com.example.moducommerce.core.model.Category
import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.model.ProductDetail
import com.example.moducommerce.core.model.ProductSort
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.core.network.safeCall
import com.example.moducommerce.data.api.CatalogApi
import javax.inject.Inject
import javax.inject.Singleton

interface CatalogRepository {
    suspend fun categories(): Result<List<Category>>

    suspend fun products(
        categoryId: Long? = null,
        query: String? = null,
        sort: ProductSort = ProductSort.LATEST,
        page: Int = 0,
        size: Int = PAGE_SIZE,
    ): Result<Page<ProductSummary>>

    suspend fun product(id: Long): Result<ProductDetail>

    suspend fun wishlist(page: Int = 0, size: Int = PAGE_SIZE): Result<Page<ProductSummary>>

    /** 찜 상태를 [wished] 로 만든다(멱등). */
    suspend fun setWished(productId: Long, wished: Boolean): Result<Unit>

    companion object {
        const val PAGE_SIZE = 20
    }
}

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val catalogApi: CatalogApi,
) : CatalogRepository {

    override suspend fun categories(): Result<List<Category>> = safeCall { catalogApi.categories().map { it.toModel() } }

    override suspend fun products(categoryId: Long?, query: String?, sort: ProductSort, page: Int, size: Int): Result<Page<ProductSummary>> =
        safeCall {
            catalogApi.products(
                categoryId = categoryId,
                query = query?.trim()?.takeIf { it.isNotEmpty() },
                sort = sort.param,
                page = page,
                size = size,
            ).toModel { it.toModel() }
        }

    override suspend fun product(id: Long): Result<ProductDetail> = safeCall { catalogApi.product(id).toModel() }

    override suspend fun wishlist(page: Int, size: Int): Result<Page<ProductSummary>> =
        safeCall { catalogApi.wishlist(page, size).toModel { it.toModel() } }

    override suspend fun setWished(productId: Long, wished: Boolean): Result<Unit> =
        safeCall { if (wished) catalogApi.addWish(productId) else catalogApi.removeWish(productId) }
}
