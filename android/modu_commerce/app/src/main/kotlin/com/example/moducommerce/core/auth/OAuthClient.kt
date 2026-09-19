package com.example.moducommerce.core.auth

/** auth-service 토큰 발급 폼. 문자열은 서버 `AuthorizationServerConfig` 에 등록된 값과 정확히 같아야 한다. */
object OAuthClient {
    const val CLIENT_ID = "modu-commerce"
    const val GRANT_SSO_CODE = "urn:modu:params:oauth:grant-type:sso_code"
    const val GRANT_GOOGLE = "urn:modu:params:oauth:grant-type:google_id_token"
    const val GRANT_REFRESH = "refresh_token"

    fun ssoCodeForm(code: String, codeVerifier: String): Map<String, String> = mapOf(
        "grant_type" to GRANT_SSO_CODE,
        "client_id" to CLIENT_ID,
        "code" to code,
        "code_verifier" to codeVerifier,
    )

    fun googleForm(idToken: String): Map<String, String> = mapOf(
        "grant_type" to GRANT_GOOGLE,
        "client_id" to CLIENT_ID,
        "id_token" to idToken,
    )

    fun refreshForm(refreshToken: String): Map<String, String> = mapOf(
        "grant_type" to GRANT_REFRESH,
        "client_id" to CLIENT_ID,
        "refresh_token" to refreshToken,
    )
}
