package com.example.moducommerce

import android.content.Context

/** 이 앱의 모두 계정 토큰. 샘플이라 SharedPreferences 에 둔다. */
class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("modu-commerce-auth", Context.MODE_PRIVATE)

    fun save(tokens: TokenResponse) {
        prefs.edit().putString("access", tokens.accessToken).putString("refresh", tokens.refreshToken).apply()
    }

    fun access(): String? = prefs.getString("access", null)
    fun refresh(): String? = prefs.getString("refresh", null)
    fun clear() = prefs.edit().clear().apply()
}
