package com.example.moducommerce.feature.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.moducommerce.R
import com.example.moducommerce.core.ui.components.CommerceTopBar
import com.example.moducommerce.feature.category.CategoryScreen
import com.example.moducommerce.feature.home.HomeScreen
import com.example.moducommerce.feature.my.MyScreen
import com.example.moducommerce.feature.wishlist.WishlistScreen

enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    CATEGORY(R.string.tab_category, Icons.Filled.GridView),
    WISHLIST(R.string.tab_wishlist, Icons.Filled.Favorite),
    MY(R.string.tab_my, Icons.Filled.Person),
}

/** 하단 탭 네 개. 탭 전환은 NavHost 가 아니라 여기서 상태로 한다(뒤로 가기는 앱을 나간다). */
@Composable
fun MainScreen(
    onOpenSearch: () -> Unit,
    onOpenCart: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenCategory: (Long, String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenAddresses: () -> Unit,
    cartCount: Int,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            CommerceTopBar(
                title = if (tab == MainTab.HOME) stringResource(R.string.app_name) else stringResource(tab.labelRes),
                actions = {
                    if (tab == MainTab.HOME || tab == MainTab.CATEGORY) {
                        IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search)) }
                    }
                    if (tab != MainTab.MY) {
                        IconButton(onClick = onOpenCart) {
                            BadgedBox(badge = { if (cartCount > 0) Badge { Text(cartCount.toString()) } }) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.cart_title))
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        androidx.compose.foundation.layout.Box(modifier = modifier) {
            when (tab) {
                MainTab.HOME -> HomeScreen(snackbarHostState = snackbarHostState, onOpenProduct = onOpenProduct, onOpenCategory = onOpenCategory)
                MainTab.CATEGORY -> CategoryScreen(onOpenCategory = onOpenCategory)
                MainTab.WISHLIST -> WishlistScreen(snackbarHostState = snackbarHostState, onOpenProduct = onOpenProduct)
                MainTab.MY -> MyScreen(onOpenOrders = onOpenOrders, onOpenAddresses = onOpenAddresses)
            }
        }
    }
}
