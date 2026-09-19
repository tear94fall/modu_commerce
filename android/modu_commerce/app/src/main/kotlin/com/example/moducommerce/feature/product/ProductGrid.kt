package com.example.moducommerce.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.moducommerce.R
import com.example.moducommerce.core.model.ProductSort
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.core.ui.components.ProductCard
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems

/** 마지막 줄이 보이면 [onLoadMore] 를 부른다. */
@Composable
fun rememberLoadMore(gridState: LazyGridState, itemCount: Int, onLoadMore: () -> Unit) {
    val shouldLoad by remember(itemCount) {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            itemCount > 0 && last >= itemCount - 4
        }
    }
    LaunchedEffect(shouldLoad, itemCount) { if (shouldLoad) onLoadMore() }
}

/** 두 칸 격자. [header] 는 격자 위에 전체 폭으로 그린다(정렬 칩, 홈의 가로줄 등). */
@Composable
fun ProductGrid(
    items: List<ProductSummary>,
    loadingMore: Boolean,
    onOpen: (Long) -> Unit,
    onToggleWish: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    header: (LazyGridScope.() -> Unit)? = null,
) {
    rememberLoadMore(gridState, items.size, onLoadMore)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        header?.invoke(this)
        items(items, key = { it.id }) { product ->
            ProductCard(product = product, onClick = { onOpen(product.id) }, onToggleWish = { onToggleWish(product.id) })
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/** 정렬 칩 한 줄. */
@Composable
fun SortChips(selected: ProductSort, onSelect: (ProductSort) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rowItems(ProductSort.entries) { sort ->
            FilterChip(selected = sort == selected, onClick = { onSelect(sort) }, label = { Text(stringResource(sort.labelRes())) })
        }
    }
}

fun ProductSort.labelRes(): Int = when (this) {
    ProductSort.LATEST -> R.string.sort_latest
    ProductSort.POPULAR -> R.string.sort_popular
    ProductSort.PRICE_ASC -> R.string.sort_price_asc
    ProductSort.PRICE_DESC -> R.string.sort_price_desc
}
