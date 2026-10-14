package com.example.moducommerce

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.example.moducommerce.core.push.NotificationPermissions
import com.example.moducommerce.core.push.PendingPushLink
import com.example.moducommerce.core.push.PushLink
import com.example.moducommerce.core.push.PushRegistrar
import com.example.moducommerce.core.session.SessionEvents
import com.example.moducommerce.core.session.SessionStore
import com.example.moducommerce.core.ui.theme.CommerceTheme
import com.example.moducommerce.navigation.CommerceNavHost
import com.example.moducommerce.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 하나뿐인 액티비티. 시스템 스플래시는 세션 판정까지만 잡아 두고, 토큰 갱신이 끝내 실패하면
 * [SessionEvents.loggedOut] 을 받아 로그인 화면으로 되돌린다.
 *
 * 푸시 알림을 눌러 들어오면(onCreate·onNewIntent) 엑스트라 path·campaignId 를 [PendingPushLink] 에 넣고, WebScreen 이 연다.
 * 웹이 알림 권한을 물으면 [NotificationPermissions] 가 여기 걸어 둔 런처로 권한 창을 띄운다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionStore: SessionStore

    @Inject lateinit var sessionEvents: SessionEvents

    @Inject lateinit var pendingPushLink: PendingPushLink

    @Inject lateinit var pushRegistrar: PushRegistrar

    @Inject lateinit var notificationPermissions: NotificationPermissions

    private var sessionDecided by mutableStateOf(false)

    /** 알림 권한 요청. STARTED 전에 등록해야 해서 필드로 만든다. */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationPermissions.onRequestResult() }

    private val requestNotificationPermission: () -> Unit = {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // 상태바 아이콘은 밝게(웹 화면이 상태바 뒤를 브랜드 레드로 칠한다). 로그인 화면은 흰 바탕이라 아이콘이 안 보이지만 잠깐이다.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        // 다시 만들어진 액티비티(다크 모드·언어·글자 크기 변경, 프로세스 복원 등)는 NavHost 가 보던 화면을 복원하고, 세션을 판정하는
        // SPLASH 는 이미 백스택에서 빠져 다시 돌지 않는다. 여기서 붙잡으면 창이 영영 그려지지 않는다(검은 화면, 터치 불가).
        // 그래서 시스템 스플래시는 처음 켤 때만 판정까지 잡아 둔다. (회전·접기는 manifest configChanges 로 재생성 자체를 막는다.)
        if (savedInstanceState != null) sessionDecided = true
        splashScreen.setKeepOnScreenCondition { !sessionDecided }

        notificationPermissions.attach(requestNotificationPermission)
        if (savedInstanceState == null) {
            // 재생성(복원)된 액티비티는 같은 인텐트를 다시 들고 오므로 처음 만들 때만 읽는다.
            takePushLink(intent)
            // 로그인돼 있으면 이 기기 토큰을 등록(갱신)한다. 로그아웃 상태면 아무것도 안 한다.
            pushRegistrar.registerCurrentAsync()
        }

        setContent {
            CommerceTheme {
                val navController = rememberNavController()

                CommerceNavHost(
                    navController = navController,
                    awaitLoggedIn = {
                        val loggedIn = sessionStore.isLoggedIn.first()
                        sessionDecided = true
                        loggedIn
                    },
                )

                LaunchedEffect(navController) {
                    sessionEvents.loggedOut.collect { expired ->
                        navController.navigate(Routes.login(expired = expired)) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takePushLink(intent)
    }

    override fun onDestroy() {
        notificationPermissions.detach(requestNotificationPermission)
        super.onDestroy()
    }

    /** 알림에서 온 인텐트면 대기 링크로 넣는다. 최근 앱 목록에서 다시 연 경우(같은 인텐트 재전달)는 무시한다. */
    private fun takePushLink(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val extras = intent.extras ?: return
        @Suppress("DEPRECATION")
        val link = PushLink.from(extras.getString(PushLink.EXTRA_PATH), extras.get(PushLink.EXTRA_CAMPAIGN_ID)?.toString()) ?: return
        pendingPushLink.offer(link)
        // 같은 인텐트를 두 번 처리하지 않게 비운다.
        intent.removeExtra(PushLink.EXTRA_PATH)
        intent.removeExtra(PushLink.EXTRA_CAMPAIGN_ID)
    }
}
