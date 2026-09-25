package com.example.moducommerce.feature.web

import android.webkit.JavascriptInterface
import com.example.moducommerce.core.di.ApplicationScope
import com.example.moducommerce.core.model.UserProfile
import com.example.moducommerce.core.session.SessionEvents
import com.example.moducommerce.core.session.SessionRefresher
import com.example.moducommerce.core.session.SessionStore
import com.example.moducommerce.data.repository.AuthRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * WebView 에 `window.ModuApp` 으로 심는 브리지. 웹(`web/src/bridge/app.ts`)과 이름·시그니처가 같아야 한다.
 * `@JavascriptInterface` 메서드는 WebView 의 전용 백그라운드 스레드에서 불리므로 여기서 [runBlocking] 해도 UI 가 멈추지 않는다.
 */
class ModuAppBridge @Inject constructor(
    private val sessionStore: SessionStore,
    private val refresher: SessionRefresher,
    private val sessionEvents: SessionEvents,
    private val authRepository: AuthRepository,
    private val gson: Gson,
    @ApplicationScope private val scope: CoroutineScope,
) {

    @JavascriptInterface
    fun getAccessToken(): String = runBlocking { sessionStore.accessToken().orEmpty() }

    /** 직전 갱신이 네트워크 문제로 실패했는가. 그 뒤에 오는 [onSessionExpired] 는 로그아웃하지 않는다. */
    private val refreshFailedTemporarily = AtomicBoolean(false)

    /**
     * 웹이 [failedToken] 으로 401 을 받았을 때. 다른 요청이 먼저 갱신해 둔 토큰이 있으면 그걸 주고, 아니면 한 번 갱신한다.
     * (현재 토큰을 실패 토큰으로 넘기면 병렬 401 마다 갱신이 반복돼 refresh 토큰 회전과 어긋난다.)
     * 실패하면 빈 문자열. 거절이면 세션 정리·로그인 이동은 [SessionRefresher] 가 이미 했고,
     * 네트워크 문제면 세션은 그대로다(웹은 그 요청의 오류 화면 + 다시 시도를 보인다).
     */
    @JavascriptInterface
    fun refreshAccessToken(failedToken: String): String {
        val token = runBlocking { refresher.refresh(failedToken.ifBlank { null }) }
        refreshFailedTemporarily.set(token == null && refresher.lastFailureTemporary)
        return token.orEmpty()
    }

    /**
     * 웹이 갱신 뒤에도 401 이라 더 진행할 수 없을 때. 갱신이 연결 문제로만 실패했으면 로그아웃하지 않는다
     * (연결이 불안정할 때 로그인이 풀리지 않게). 그 밖에는 세션을 지우고 로그인 화면으로 간다.
     */
    @JavascriptInterface
    fun onSessionExpired() {
        if (refreshFailedTemporarily.getAndSet(false)) return
        scope.launch {
            sessionStore.clearSession()
            sessionEvents.notifyLoggedOut(expired = true)
        }
    }

    @JavascriptInterface
    fun logout() {
        scope.launch { authRepository.logout() }
    }

    @JavascriptInterface
    fun getProfile(): String = gson.toJson(runBlocking { sessionStore.profile.first() } ?: UserProfile())

    companion object {
        const val NAME = "ModuApp"
    }
}
