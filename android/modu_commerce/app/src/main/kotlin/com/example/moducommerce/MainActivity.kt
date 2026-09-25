package com.example.moducommerce

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
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
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionStore: SessionStore

    @Inject lateinit var sessionEvents: SessionEvents

    private var sessionDecided by mutableStateOf(false)

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
}
