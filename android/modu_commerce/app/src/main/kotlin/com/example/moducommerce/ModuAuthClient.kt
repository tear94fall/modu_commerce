package com.example.moducommerce

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** auth-service 호출. 동기 OkHttp 라 백그라운드 스레드에서 쓴다. client_id 는 modu-commerce. */
class ModuAuthClient(baseUrl: String) {

    class AuthException(val status: Int, body: String) : IOException("HTTP $status: $body")

    private val baseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val http = OkHttpClient()

    fun exchangeSsoCode(code: String, codeVerifier: String): TokenResponse = token(
        FormBody.Builder()
            .add("grant_type", GRANT_SSO_CODE)
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .build()
    )

    fun exchangeGoogleIdToken(idToken: String): TokenResponse = token(
        FormBody.Builder()
            .add("grant_type", GRANT_GOOGLE)
            .add("client_id", CLIENT_ID)
            .add("id_token", idToken)
            .build()
    )

    fun refresh(refreshToken: String): TokenResponse = token(
        FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", CLIENT_ID)
            .add("refresh_token", refreshToken)
            .build()
    )

    fun revoke(refreshToken: String) {
        val req = Request.Builder()
            .url(baseUrl + "auth-service/oauth2/revoke")
            .post(FormBody.Builder().add("token", refreshToken).add("client_id", CLIENT_ID).build())
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw AuthException(res.code, res.body?.string() ?: "")
        }
    }

    /** OIDC userinfo. name/email/picture 를 돌려준다. */
    fun userinfo(accessToken: String): String {
        val req = Request.Builder()
            .url(baseUrl + "auth-service/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw AuthException(res.code, body)
            return body
        }
    }

    private fun token(form: FormBody): TokenResponse {
        val req = Request.Builder().url(baseUrl + "auth-service/oauth2/token").post(form).build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw AuthException(res.code, body)
            return TokenResponse.parse(body)
        }
    }

    companion object {
        const val CLIENT_ID = "modu-commerce"
        const val GRANT_SSO_CODE = "urn:modu:params:oauth:grant-type:sso_code"
        const val GRANT_GOOGLE = "urn:modu:params:oauth:grant-type:google_id_token"
    }
}
