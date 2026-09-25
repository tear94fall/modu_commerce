package com.example.moducommerce.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.moducommerce.core.ui.components.CenteredBranding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** 시스템 스플래시가 그리던 그림을 이어받아 최소 시간만큼 머문 뒤 로그인/메인으로 간다. */
@Composable
fun SplashScreen(awaitLoggedIn: suspend () -> Boolean, onDecided: (Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        val loggedIn = coroutineScope {
            val decision = async { awaitLoggedIn() }
            delay(MIN_VISIBLE_MS)
            decision.await()
        }
        onDecided(loggedIn)
    }
    // 로고는 시스템 스플래시와 같은 자리(창 정중앙)에 둔다. 넘어가는 순간 로고가 움직이지 않는다.
    CenteredBranding(modifier = Modifier.fillMaxSize().background(Color.White))
}

private const val MIN_VISIBLE_MS = 600L
