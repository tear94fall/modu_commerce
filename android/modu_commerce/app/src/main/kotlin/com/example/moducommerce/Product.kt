package com.example.moducommerce

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.text.NumberFormat
import java.util.Locale

/** commerce-service GET /api/v1/products 응답 항목 */
data class Product(
    val id: Long = 0,
    val name: String? = null,
    val description: String? = null,
    val price: Long = 0,
    @SerializedName("imageUrl") val imageUrl: String? = null,
) {
    fun priceLabel(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(price) + "원"

    /** 쓸 수 있는 이미지 주소. 비어 있으면 목록에서 플레이스홀더를 깐다. */
    fun imageUrlOrNull(): String? = imageUrl?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        fun parse(json: String): Product = Gson().fromJson(json, Product::class.java)

        fun parseList(json: String): List<Product> = Gson().fromJson(json, object : TypeToken<List<Product>>() {}.type)
    }
}
