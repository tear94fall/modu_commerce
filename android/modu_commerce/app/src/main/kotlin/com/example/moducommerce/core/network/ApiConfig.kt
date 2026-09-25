package com.example.moducommerce.core.network

import com.example.moducommerce.BuildConfig

object ApiConfig {
    /** 게이트웨이. auth-service 토큰·userinfo 를 부른다. */
    val GATEWAY_URL: String = BuildConfig.API_BASE_URL

    /** 커머스 화면(web/). WebView 가 연다. */
    val WEB_URL: String = BuildConfig.WEB_URL
}
