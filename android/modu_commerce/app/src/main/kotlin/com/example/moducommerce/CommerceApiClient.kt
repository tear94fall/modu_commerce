package com.example.moducommerce

import okhttp3.OkHttpClient
import okhttp3.Request

/** commerce-service 호출. auth-service 가 발급한 modu-commerce 액세스 토큰을 Bearer 로 보낸다. */
class CommerceApiClient(baseUrl: String) {

    private val baseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val http = OkHttpClient()

    fun products(accessToken: String): List<Product> {
        val req = Request.Builder()
            .url(baseUrl + "api/v1/products")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ModuAuthClient.AuthException(res.code, body)
            return Product.parseList(body)
        }
    }
}
