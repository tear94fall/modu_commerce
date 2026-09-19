package com.example.moducommerce.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.moducommerce.core.ui.components.BrandingHeader
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
    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) { BrandingHeader() }
}

private const val MIN_VISIBLE_MS = 600L
