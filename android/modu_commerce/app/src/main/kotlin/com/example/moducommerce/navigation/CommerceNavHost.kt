package com.example.moducommerce.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.data.repository.OrderRepository
import com.example.moducommerce.feature.cart.CartScreen
import com.example.moducommerce.feature.checkout.AddressesScreen
import com.example.moducommerce.feature.checkout.CheckoutScreen
import com.example.moducommerce.feature.login.LoginScreen
import com.example.moducommerce.feature.orders.OrderDetailScreen
import com.example.moducommerce.feature.orders.OrdersScreen
import com.example.moducommerce.feature.main.MainScreen
import com.example.moducommerce.feature.product.ProductDetailScreen
import com.example.moducommerce.feature.product.ProductListScreen
import com.example.moducommerce.feature.search.SearchScreen
import com.example.moducommerce.feature.splash.SplashScreen

@Composable
fun CommerceNavHost(
    navController: NavHostController,
    awaitLoggedIn: suspend () -> Boolean,
    orderRepository: OrderRepository,
    modifier: Modifier = Modifier,
) {
    val cartCount by orderRepository.cartCount.collectAsStateWithLifecycle()
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
                onOpenCart = { navController.navigate(Routes.CART) },
                onOpenProduct = { navController.navigate(Routes.product(it)) },
                onOpenCategory = { id, title -> navController.navigate(Routes.products(id, title)) },
                onOpenOrders = { navController.navigate(Routes.ORDERS) },
                onOpenAddresses = { navController.navigate(Routes.ADDRESSES) },
                cartCount = cartCount,
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
            ProductDetailScreen(
                onBack = { navController.popBackStack() },
                onBuyNow = { productId, skuId, quantity -> navController.navigate(Routes.checkoutDirect(productId, skuId, quantity)) },
            )
        }
        composable(Routes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { navController.navigate(Routes.product(it)) },
                onCheckout = { ids -> navController.navigate(Routes.checkoutFromCart(ids)) },
            )
        }
        composable(
            Routes.CHECKOUT,
            arguments = listOf(
                navArgument(Routes.ARG_CART_ITEM_IDS) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ARG_PRODUCT_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(Routes.ARG_SKU_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(Routes.ARG_QUANTITY) { type = NavType.IntType; defaultValue = 1 },
            ),
        ) {
            CheckoutScreen(
                onBack = { navController.popBackStack() },
                // 주문서와 장바구니를 걷어내고 주문 상세로. 뒤로 가면 메인이다.
                onOrdered = { orderId ->
                    navController.navigate(Routes.order(orderId)) {
                        popUpTo(Routes.MAIN) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.ORDERS) {
            OrdersScreen(onBack = { navController.popBackStack() }, onOpenOrder = { navController.navigate(Routes.order(it)) })
        }
        composable(Routes.ORDER, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })) {
            OrderDetailScreen(onBack = { navController.popBackStack() }, onOpenProduct = { navController.navigate(Routes.product(it)) })
        }
        composable(Routes.ADDRESSES) {
            AddressesScreen(onBack = { navController.popBackStack() })
        }
    }
}
