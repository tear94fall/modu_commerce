package com.example.moducommerce.data.api

import com.example.moducommerce.data.dto.CategoryDto
import com.example.moducommerce.data.dto.PageDto
import com.example.moducommerce.data.dto.ProductDetailDto
import com.example.moducommerce.data.dto.ProductSummaryDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** commerce-service `/api/v1`. 모두 계정 토큰(aud=modu-commerce)이 필요하다. */
interface CatalogApi {

    @GET("api/v1/categories")
    suspend fun categories(): List<CategoryDto>

    @GET("api/v1/products")
    suspend fun products(
        @Query("categoryId") categoryId: Long? = null,
        @Query("q") query: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<ProductSummaryDto>

    @GET("api/v1/products/{id}")
    suspend fun product(@Path("id") id: Long): ProductDetailDto

    @GET("api/v1/wishlist")
    suspend fun wishlist(@Query("page") page: Int = 0, @Query("size") size: Int = 20): PageDto<ProductSummaryDto>

    @POST("api/v1/wishlist/{productId}")
    suspend fun addWish(@Path("productId") productId: Long)

    @DELETE("api/v1/wishlist/{productId}")
    suspend fun removeWish(@Path("productId") productId: Long)
}
