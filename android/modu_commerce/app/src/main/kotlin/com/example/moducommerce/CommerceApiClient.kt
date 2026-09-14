package com.example.moducommerce

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** commerce-service 호출. auth-service 가 발급한 modu-commerce 액세스 토큰을 Bearer 로 보낸다. */
class CommerceApiClient(baseUrl: String) {

    private val baseUrl = withSlash(baseUrl)
    private val http = OkHttpClient()

    fun products(accessToken: String, query: String? = null): List<Product> =
        Product.parseList(get(productsUrl(baseUrl, query), accessToken))

    /** 삭제됐거나 없는 상품이면 AuthException(404). */
    fun product(accessToken: String, id: Long): Product =
        Product.parse(get((baseUrl + "api/v1/products/$id").toHttpUrl(), accessToken))

    private fun get(url: HttpUrl, accessToken: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ModuAuthClient.AuthException(res.code, body)
            return body
        }
    }

    companion object {
        private fun withSlash(url: String) = if (url.endsWith("/")) url else "$url/"

        /** 검색어가 비면 q 를 붙이지 않는다. 한글은 OkHttp 가 UTF-8 로 인코딩한다. */
        fun productsUrl(baseUrl: String, query: String?): HttpUrl {
            val builder = (withSlash(baseUrl) + "api/v1/products").toHttpUrl().newBuilder()
            query?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.addQueryParameter("q", it) }
            return builder.build()
        }
    }
}
