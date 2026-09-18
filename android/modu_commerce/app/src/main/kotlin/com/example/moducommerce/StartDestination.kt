package com.example.moducommerce

/** 초기 화면이 어디로 보낼지. 저장된 액세스 토큰 하나로만 정한다. */
enum class StartDestination {
    LOGIN,
    PRODUCTS,
    ;

    companion object {
        fun of(accessToken: String?): StartDestination =
            if (accessToken.isNullOrBlank()) LOGIN else PRODUCTS
    }
}
