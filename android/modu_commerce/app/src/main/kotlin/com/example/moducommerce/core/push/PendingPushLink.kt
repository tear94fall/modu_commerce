package com.example.moducommerce.core.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 알림을 눌러 들어온 뒤 아직 웹에서 열지 못한 링크. MainActivity 가 넣고 WebScreen 이 꺼내 쓴다.
 * 로그인 전이면 로그인 → 웹 화면이 뜰 때까지 여기 남아 있다. 새로 누른 알림이 이전 것을 덮는다.
 */
@Singleton
class PendingPushLink @Inject constructor() {

    private val _link = MutableStateFlow<PushLink?>(null)
    val link: StateFlow<PushLink?> = _link.asStateFlow()

    fun offer(link: PushLink) {
        _link.value = link
    }

    /** [link] 가 아직 대기 중이면 꺼낸다. 그 사이 다른 링크가 들어왔으면 false(그건 다음 차례에 연다). */
    fun take(link: PushLink): Boolean = _link.compareAndSet(link, null)

    /** 대기 중인 링크를 꺼낸다(웹을 처음 열 때). */
    fun takeAny(): PushLink? {
        while (true) {
            val current = _link.value ?: return null
            if (_link.compareAndSet(current, null)) return current
        }
    }
}
