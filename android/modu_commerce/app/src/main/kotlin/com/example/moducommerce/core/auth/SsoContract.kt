package com.example.moducommerce.core.auth

import android.content.Intent

/** 모두의 채팅 앱의 SSO 액티비티 계약. 인텐트 모양은 채팅 앱 `SsoActivity` 와 같아야 한다. */
object SsoContract {
    const val CHAT_PACKAGE = "com.example.modumessenger"
    const val ACTION = "com.example.modumessenger.action.REQUEST_SSO_CODE"
    const val EXTRA_CODE = "code"
    const val EXTRA_REASON = "reason"

    fun requestIntent(codeChallenge: String): Intent = Intent(ACTION).apply {
        setPackage(CHAT_PACKAGE)
        putExtra("client_id", OAuthClient.CLIENT_ID)
        putExtra("code_challenge", codeChallenge)
        putExtra("code_challenge_method", "S256")
    }
}
