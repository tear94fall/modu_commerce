package com.example.moducommerce.feature.web

import androidx.lifecycle.ViewModel
import com.example.moducommerce.core.push.NotificationPermissions
import com.example.moducommerce.core.push.PendingPushLink
import com.example.moducommerce.core.push.PushLink
import com.example.moducommerce.core.push.PushRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 브리지를 Hilt 로 만들어 WebScreen 에 건넨다. 푸시 딥링크와 권한 결과도 여기서 받는다. */
@HiltViewModel
class WebViewModel @Inject constructor(
    val bridge: ModuAppBridge,
    val notificationPermissions: NotificationPermissions,
    private val pendingPushLink: PendingPushLink,
    private val pushRegistrar: PushRegistrar,
) : ViewModel() {

    val pendingLink = pendingPushLink.link

    /** WebView 를 처음 만들 때: 대기 중인 링크가 있으면 첫 주소로 쓴다. */
    fun takeInitialLink(): PushLink? = pendingPushLink.takeAny()?.also(::opened)

    /** 떠 있는 웹에서 [link] 를 열 차례. 그새 다른 링크로 바뀌었으면 false. */
    fun take(link: PushLink): Boolean = pendingPushLink.take(link).also { if (it) opened(link) }

    /** 캠페인 알림이면 열람을 알린다(웹 화면은 로그인 상태에서만 뜬다). */
    private fun opened(link: PushLink) {
        link.campaignId?.let(pushRegistrar::reportOpenedAsync)
    }
}
