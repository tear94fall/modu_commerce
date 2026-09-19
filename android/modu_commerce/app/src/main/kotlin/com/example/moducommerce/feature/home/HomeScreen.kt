package com.example.moducommerce.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SnackbarHostState
import com.example.moducommerce.R
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.components.ProductCard
import com.example.moducommerce.feature.product.ProductGrid

@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    onOpenProduct: (Long) -> Unit,
    onOpenCategory: (Long, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.messageRes) {
        val res = state.messageRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(res))
        viewModel.consumeMessage()
    }

    when {
        state.loading -> LoadingBox()
        state.error -> ErrorBox(message = stringResource(R.string.products_failed), onRetry = viewModel::load)
        else -> ProductGrid(
            items = state.items,
            loadingMore = state.loadingMore,
            onOpen = onOpenProduct,
            onToggleWish = viewModel::toggleWish,
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            header = {
                if (sections.categories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sections.categories, key = { it.id }) { category ->
                                AssistChip(onClick = { onOpenCategory(category.id, category.name) }, label = { Text(category.name) })
                            }
                        }
                    }
                }
                if (sections.newest.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ProductRow(title = stringResource(R.string.home_new), products = sections.newest, onOpen = onOpenProduct, onToggleWish = viewModel::toggleWish)
                    }
                }
                if (sections.popular.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ProductRow(title = stringResource(R.string.home_popular), products = sections.popular, onOpen = onOpenProduct, onToggleWish = viewModel::toggleWish)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.home_all)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ProductRow(title: String, products: List<ProductSummary>, onOpen: (Long) -> Unit, onToggleWish: (Long) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SectionTitle(title) }
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products, key = { it.id }) { product ->
                ProductCard(product = product, onClick = { onOpen(product.id) }, onToggleWish = { onToggleWish(product.id) }, modifier = Modifier.width(150.dp))
            }
        }
    }
}
