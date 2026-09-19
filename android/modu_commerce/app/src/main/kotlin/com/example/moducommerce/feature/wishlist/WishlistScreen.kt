package com.example.moducommerce.feature.wishlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.ui.components.EmptyBox
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.feature.product.ProductGrid

@Composable
fun WishlistScreen(
    snackbarHostState: SnackbarHostState,
    onOpenProduct: (Long) -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.messageRes) {
        val res = state.messageRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(res))
        viewModel.consumeMessage()
    }

    when {
        state.loading -> LoadingBox()
        state.error -> ErrorBox(message = stringResource(R.string.products_failed), onRetry = viewModel::load)
        state.items.isEmpty() -> EmptyBox(message = stringResource(R.string.wishlist_empty))
        else -> ProductGrid(
            items = state.items,
            loadingMore = state.loadingMore,
            onOpen = onOpenProduct,
            onToggleWish = viewModel::toggleWish,
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
