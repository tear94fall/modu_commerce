package com.example.moducommerce.core.session

import com.example.moducommerce.core.auth.OAuthClient
import com.example.moducommerce.data.api.AuthApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * refresh_token 으로 액세스 토큰을 갱신한다. OkHttp 의 [com.example.moducommerce.core.network.TokenAuthenticator] 와
 * WebView 브리지가 같이 쓴다.
 * - 단일 비행: 여러 쪽이 동시에 401 을 받아도 갱신은 한 번만 한다. 뒤늦게 온 쪽은 이미 바뀐 토큰을 받는다.
 * - 갱신 실패 → 세션을 지우고 [SessionEvents.notifyLoggedOut] 으로 로그인 화면으로 보낸다.
 */
@Singleton
class SessionRefresher @Inject constructor(
    private val sessionStore: SessionStore,
    private val sessionEvents: SessionEvents,
    @Named("plain") private val plainAuthApi: AuthApi,
) {

    private val mutex = Mutex()

    /** [failedToken] 으로 401 을 받았을 때 쓸 새 토큰. 갱신할 수 없으면 null(세션은 이미 정리됨). */
    suspend fun refresh(failedToken: String?): String? = mutex.withLock {
        val current = sessionStore.accessToken()
        if (!current.isNullOrBlank() && current != failedToken) return@withLock current
        val refresh = sessionStore.refreshToken()
        if (refresh.isNullOrBlank()) {
            giveUp()
            return@withLock null
        }
        val tokens = runCatching { plainAuthApi.token(OAuthClient.refreshForm(refresh)) }.getOrNull()
        val newAccess = tokens?.accessToken
        if (newAccess.isNullOrBlank()) {
            giveUp()
            return@withLock null
        }
        sessionStore.saveTokens(newAccess, tokens.refreshToken ?: refresh)
        newAccess
    }

    private suspend fun giveUp() {
        sessionStore.clearSession()
        sessionEvents.notifyLoggedOut(expired = true)
    }
}
