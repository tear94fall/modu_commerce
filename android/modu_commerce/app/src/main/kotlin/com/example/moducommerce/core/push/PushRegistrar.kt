package com.example.moducommerce.core.push

import android.util.Log
import com.example.moducommerce.core.di.ApplicationScope
import com.example.moducommerce.core.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 이 기기의 푸시 토큰을 commerce-service 에 등록·해제하고, 캠페인 알림 열람을 알린다.
 * 전부 최선 노력이다: 실패는 로그만 남기고 로그인·로그아웃·화면 이동을 막지 않는다.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val pushApi: PushApi,
    private val tokens: PushTokenSource,
    private val sessionStore: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** 로그인 직후·앱 시작 때. 로그인돼 있을 때만 등록한다. */
    fun registerCurrentAsync(): Job = scope.launch { registerCurrent() }

    suspend fun registerCurrent() {
        if (sessionStore.accessToken() == null) return
        val token = tokens.currentToken() ?: return
        register(token)
    }

    /** FirebaseMessagingService.onNewToken. 로그아웃 상태면 다음 로그인 때 등록된다. */
    fun onNewToken(token: String): Job = scope.launch {
        if (sessionStore.accessToken() == null) return@launch
        register(token)
    }

    /**
     * 로그아웃 직전(세션을 지우기 전)에 부른다. 이 기기로 더는 보내지 않게 토큰을 지운다.
     * 느린 네트워크가 로그아웃을 붙잡지 않도록 [UNREGISTER_TIMEOUT_MS] 에서 끊는다.
     */
    suspend fun unregisterCurrent() {
        if (sessionStore.accessToken() == null) return
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
            val token = tokens.currentToken() ?: return@withTimeoutOrNull
            call("unregister") { pushApi.unregisterDevice(token) }
        }
    }

    /** 캠페인 알림을 눌러 들어왔다. 로그인된 웹 화면에서 부른다. */
    fun reportOpenedAsync(campaignId: Long): Job = scope.launch { call("opened $campaignId") { pushApi.reportOpened(campaignId) } }

    private suspend fun register(token: String) {
        call("register") { pushApi.registerDevice(PushDeviceRequest(token = token)) }
    }

    private suspend fun call(what: String, block: suspend () -> Response<Unit>) {
        try {
            val res = block()
            if (!res.isSuccessful) Log.w(TAG, "$what -> HTTP ${res.code()}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "PushRegistrar"
        const val UNREGISTER_TIMEOUT_MS = 3_000L
    }
}
