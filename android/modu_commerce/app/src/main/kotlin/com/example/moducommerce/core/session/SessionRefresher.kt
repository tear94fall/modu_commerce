package com.example.moducommerce.core.session

import com.example.moducommerce.core.auth.OAuthClient
import com.example.moducommerce.data.api.AuthApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * refresh_token 으로 액세스 토큰을 갱신한다. OkHttp 의 [com.example.moducommerce.core.network.TokenAuthenticator] 와
 * WebView 브리지가 같이 쓴다.
 * - 단일 비행: 여러 쪽이 동시에 401 을 받아도 갱신은 한 번만 한다. 뒤늦게 온 쪽은 이미 바뀐 토큰을 받는다.
 * - 서버가 refresh 토큰을 거절(400·401)하면 세션을 지우고 [SessionEvents.notifyLoggedOut] 으로 로그인 화면으로 보낸다.
 * - 네트워크 오류·서버 일시 장애(5xx)는 세션을 그대로 두고 null 만 준다([lastFailureTemporary] = true).
 *   연결이 불안정할 때 갱신 요청 하나가 실패했다고 로그아웃되면 안 된다. 그 요청만 실패하고 다음 요청이 다시 갱신한다.
 */
@Singleton
class SessionRefresher @Inject constructor(
    private val sessionStore: SessionStore,
    private val sessionEvents: SessionEvents,
    @Named("plain") private val plainAuthApi: AuthApi,
) {

    private val mutex = Mutex()

    /** 마지막 [refresh] 가 null 을 줬을 때, 그게 일시적 실패(세션 유지)였는지. 브리지가 로그아웃 여부를 정할 때 본다. */
    @Volatile
    var lastFailureTemporary: Boolean = false
        private set

    /**
     * [failedToken] 으로 401 을 받았을 때 쓸 새 토큰. 갱신할 수 없으면 null.
     * 거절이면 세션을 이미 정리했고, 일시적 실패면 세션은 그대로다([lastFailureTemporary]).
     */
    suspend fun refresh(failedToken: String?): String? = mutex.withLock {
        lastFailureTemporary = false
        val current = sessionStore.accessToken()
        if (!current.isNullOrBlank() && current != failedToken) return@withLock current
        val refresh = sessionStore.refreshToken()
        if (refresh.isNullOrBlank()) {
            giveUp()
            return@withLock null
        }
        val tokens =
            try {
                plainAuthApi.token(OAuthClient.refreshForm(refresh))
            } catch (e: HttpException) {
                if (e.code() == 400 || e.code() == 401) giveUp() else lastFailureTemporary = true
                return@withLock null
            } catch (e: Exception) {
                // 연결 실패·타임아웃 등. 세션은 살아 있다.
                lastFailureTemporary = true
                return@withLock null
            }
        val newAccess = tokens.accessToken
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
