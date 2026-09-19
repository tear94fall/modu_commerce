package com.example.moducommerce.data.api

import com.example.moducommerce.data.dto.AddCartItemRequestDto
import com.example.moducommerce.data.dto.AddressDto
import com.example.moducommerce.data.dto.AddressRequestDto
import com.example.moducommerce.data.dto.CartDto
import com.example.moducommerce.data.dto.CartItemDto
import com.example.moducommerce.data.dto.ChangeQuantityRequestDto
import com.example.moducommerce.data.dto.CreateOrderRequestDto
import com.example.moducommerce.data.dto.OrderDetailDto
import com.example.moducommerce.data.dto.OrderSummaryDto
import com.example.moducommerce.data.dto.PageDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** commerce-service 장바구니·배송지·주문. */
interface OrderApi {

    @GET("api/v1/cart")
    suspend fun cart(): CartDto

    @POST("api/v1/cart/items")
    suspend fun addCartItem(@Body body: AddCartItemRequestDto): CartItemDto

    @PATCH("api/v1/cart/items/{id}")
    suspend fun changeQuantity(@Path("id") id: Long, @Body body: ChangeQuantityRequestDto): CartItemDto

    @DELETE("api/v1/cart/items/{id}")
    suspend fun removeCartItem(@Path("id") id: Long)

    @GET("api/v1/addresses")
    suspend fun addresses(): List<AddressDto>

    @POST("api/v1/addresses")
    suspend fun createAddress(@Body body: AddressRequestDto): AddressDto

    @PUT("api/v1/addresses/{id}")
    suspend fun updateAddress(@Path("id") id: Long, @Body body: AddressRequestDto): AddressDto

    @PUT("api/v1/addresses/{id}/default")
    suspend fun setDefaultAddress(@Path("id") id: Long): AddressDto

    @DELETE("api/v1/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: Long)

    @POST("api/v1/orders")
    suspend fun createOrder(@Body body: CreateOrderRequestDto): OrderDetailDto

    @GET("api/v1/orders")
    suspend fun orders(@Query("page") page: Int = 0, @Query("size") size: Int = 20): PageDto<OrderSummaryDto>

    @GET("api/v1/orders/{id}")
    suspend fun order(@Path("id") id: Long): OrderDetailDto

    @POST("api/v1/orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") id: Long): OrderDetailDto
}
