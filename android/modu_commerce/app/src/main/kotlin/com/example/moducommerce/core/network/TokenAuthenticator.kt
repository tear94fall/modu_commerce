package com.example.moducommerce.core.network

import com.example.moducommerce.core.session.SessionRefresher
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/** 401 을 받으면 [SessionRefresher] 로 한 번만 갱신하고 요청을 재시도한다(모두의 채팅과 같은 규칙). */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val refresher: SessionRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val newToken = runBlocking { refresher.refresh(failedToken) } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $newToken").build()
    }
}
