package com.example.moducommerce.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.moducommerce.feature.login.LoginScreen
import com.example.moducommerce.feature.main.MainScreen
import com.example.moducommerce.feature.product.ProductDetailScreen
import com.example.moducommerce.feature.product.ProductListScreen
import com.example.moducommerce.feature.search.SearchScreen
import com.example.moducommerce.feature.splash.SplashScreen

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
                    navController.navigate(if (loggedIn) Routes.MAIN else Routes.login()) {
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
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenProduct = { navController.navigate(Routes.product(it)) },
                onOpenCategory = { id, title -> navController.navigate(Routes.products(id, title)) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(onBack = { navController.popBackStack() }, onOpenProduct = { navController.navigate(Routes.product(it)) })
        }
        composable(
            Routes.PRODUCTS,
            arguments = listOf(
                navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(Routes.ARG_TITLE) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ProductListScreen(onBack = { navController.popBackStack() }, onOpenProduct = { navController.navigate(Routes.product(it)) })
        }
        composable(Routes.PRODUCT, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })) {
            ProductDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
