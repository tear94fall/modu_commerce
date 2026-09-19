package com.example.moducommerce.feature.product

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.ui.components.CommerceTopBar
import com.example.moducommerce.core.ui.components.EmptyBox
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox

/** 카테고리 상품 목록. 정렬 칩은 격자 머리로 같이 스크롤된다. */
@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    viewModel: ProductListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.messageRes) {
        val res = state.messageRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(res))
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = { CommerceTopBar(title = viewModel.title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.error -> ErrorBox(message = stringResource(R.string.products_failed), modifier = Modifier.padding(padding), onRetry = viewModel::retry)
            else -> ProductGrid(
                items = state.items,
                loadingMore = state.loadingMore,
                onOpen = onOpenProduct,
                onToggleWish = viewModel::toggleWish,
                onLoadMore = viewModel::loadMore,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                header = {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SortChips(selected = sort, onSelect = viewModel::onSortChange, modifier = Modifier.fillMaxWidth())
                    }
                    if (state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyBox(message = stringResource(R.string.products_empty), modifier = Modifier.padding(top = 80.dp)) }
                    }
                },
            )
        }
    }
}
