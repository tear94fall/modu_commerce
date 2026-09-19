package com.example.moducommerce

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                    sessionEvents.loggedOut.collect {
                        navController.navigate(Routes.login(expired = true)) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }
}
