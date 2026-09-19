package com.example.moducommerce.core.network

import com.example.moducommerce.BuildConfig

/** 서버 주소 한 곳. 둘 다 끝에 `/` 가 붙어 있다(Retrofit base url 규칙). */
object ApiConfig {
    /** 게이트웨이. auth-service(토큰·userinfo)를 이 뒤로 부른다. */
    val GATEWAY_URL: String = BuildConfig.API_BASE_URL

    /** 커머스 서비스. 게이트웨이를 거치지 않고 직접 부른다. */
    val COMMERCE_URL: String = BuildConfig.COMMERCE_API_URL
}
