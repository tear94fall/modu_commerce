package com.example.moducommerce.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.moducommerce.feature.login.LoginScreen
import com.example.moducommerce.feature.splash.SplashScreen
import com.example.moducommerce.feature.web.WebScreen

/** 스플래시 → 로그인 또는 웹. 커머스 화면은 전부 WebScreen 안의 웹이 그린다. */
@Composable
fun CommerceNavHost(
    navController: NavHostController,
    awaitLoggedIn: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH, modifier = modifier) {
        composable(Routes.SPLASH) {
            SplashScreen(
                awaitLoggedIn = awaitLoggedIn,
                onDecided = { loggedIn ->
                    navController.navigate(if (loggedIn) Routes.WEB else Routes.login()) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            Routes.LOGIN,
            arguments = listOf(navArgument(Routes.ARG_EXPIRED) { type = NavType.BoolType; defaultValue = false }),
        ) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.WEB) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.WEB) { WebScreen() }
    }
}
