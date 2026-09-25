package com.example.moducommerce.core.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** "이제 로그인 화면으로 가야 한다" 를 앱 전체에 알린다. 값은 세션이 만료돼서인지(true) 사용자가 로그아웃해서인지(false). */
@Singleton
class SessionEvents @Inject constructor() {

    private val _loggedOut = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val loggedOut: SharedFlow<Boolean> = _loggedOut.asSharedFlow()

    fun notifyLoggedOut(expired: Boolean = true) {
        _loggedOut.tryEmit(expired)
    }
}
