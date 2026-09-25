package com.example.moducommerce.data.repository

import com.example.moducommerce.core.auth.OAuthClient
import com.example.moducommerce.core.model.UserProfile
import com.example.moducommerce.core.network.ApiException
import com.example.moducommerce.core.network.safeCall
import com.example.moducommerce.core.session.SessionEvents
import com.example.moducommerce.core.session.SessionStore
import com.example.moducommerce.data.api.AuthApi
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface AuthRepository {
    /** 채팅 앱이 준 1회용 코드를 이 앱 토큰으로 바꾸고 프로필까지 받아 저장한다. */
    suspend fun loginWithSsoCode(code: String, codeVerifier: String): Result<UserProfile>

    suspend fun loginWithGoogle(idToken: String): Result<UserProfile>

    /** revoke 를 시도하고(실패해도) 세션을 지운 뒤 앱 전체에 알린다. */
    suspend fun logout()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @Named("plain") private val plainAuthApi: AuthApi,
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val sessionEvents: SessionEvents,
) : AuthRepository {

    override suspend fun loginWithSsoCode(code: String, codeVerifier: String): Result<UserProfile> =
        exchange(OAuthClient.ssoCodeForm(code, codeVerifier))

    override suspend fun loginWithGoogle(idToken: String): Result<UserProfile> = exchange(OAuthClient.googleForm(idToken))

    override suspend fun logout() {
        val refresh = sessionStore.refreshToken()
        if (!refresh.isNullOrBlank()) safeCall { plainAuthApi.revoke(refresh, OAuthClient.CLIENT_ID) }
        sessionStore.clearSession()
        sessionEvents.notifyLoggedOut(expired = false)
    }

    private suspend fun exchange(form: Map<String, String>): Result<UserProfile> = safeCall {
        val tokens = plainAuthApi.token(form)
        val access = tokens.accessToken?.takeIf { it.isNotBlank() } ?: throw ApiException(500, "토큰 응답에 access_token 이 없습니다")
        sessionStore.saveTokens(access, tokens.refreshToken.orEmpty())
        // 프로필은 못 받아도 로그인은 성공이다. 이름은 마이 탭에서 비어 보일 뿐이다.
        val profile = runCatching { authApi.userinfo().toModel() }.getOrDefault(UserProfile())
        sessionStore.saveProfile(profile)
        profile
    }
}
