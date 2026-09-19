package com.example.moducommerce.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.model.ProductDetail
import com.example.moducommerce.core.ui.components.BottomPanel
import com.example.moducommerce.core.ui.components.CommerceTopBar
import com.example.moducommerce.core.ui.components.EmptyBox
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.components.PriceRow
import com.example.moducommerce.core.ui.components.ProductImage
import com.example.moducommerce.core.ui.theme.BrandRed
import com.example.moducommerce.core.ui.theme.ModuGrey
import com.example.moducommerce.core.util.formatPrice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onBuyNow: (productId: Long, skuId: Long, quantity: Int) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.buyNow.collect { onBuyNow(it.productId, it.skuId, it.quantity) } }

    LaunchedEffect(state.messageRes) {
        val res = state.messageRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(res))
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = { CommerceTopBar(title = state.detail?.name ?: "", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val detail = state.detail ?: return@Scaffold
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedIconButton(onClick = viewModel::toggleWish, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (detail.wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.detail_wish),
                            tint = if (detail.wished) BrandRed else ModuGrey,
                        )
                    }
                    Button(onClick = viewModel::openSheet, modifier = Modifier.weight(1f).height(48.dp), enabled = !detail.soldOut) {
                        Text(stringResource(if (detail.soldOut) R.string.sold_out else R.string.detail_select_option))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.notFound -> EmptyBox(message = stringResource(R.string.detail_not_found), modifier = Modifier.padding(padding))
            state.error -> ErrorBox(message = stringResource(R.string.detail_failed), modifier = Modifier.padding(padding), onRetry = viewModel::load)
            else -> state.detail?.let { DetailBody(detail = it, modifier = Modifier.padding(padding)) }
        }
    }

    BottomPanel(visible = state.sheetOpen && state.detail != null, onDismiss = viewModel::closeSheet) {
        OptionSheet(state = state, onSelect = viewModel::selectValue, onQuantity = viewModel::changeQuantity, onAddToCart = viewModel::addToCart, onBuyNow = viewModel::buyNow)
    }
}

@Composable
private fun DetailBody(detail: ProductDetail, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { detail.images.size.coerceAtLeast(1) })
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    ProductImage(
                        url = detail.images.getOrNull(page),
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = stringResource(R.string.detail_image, page + 1),
                    )
                }
                if (detail.images.size > 1) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(detail.images.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (index == pagerState.currentPage) BrandRed else Color.White.copy(alpha = 0.8f)),
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (detail.categoryPath.isNotEmpty()) {
                    Text(text = detail.categoryPath.joinToString(" > "), style = MaterialTheme.typography.labelMedium, color = ModuGrey)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(text = detail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                PriceRow(price = detail.price, listPrice = detail.listPrice, discountRate = detail.discountRate, large = true)
                if (detail.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = detail.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (detail.optionGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = detail.optionGroups.joinToString(" · ") { g -> "${g.name}: ${g.values.joinToString(", ") { it.name }}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ModuGrey,
                    )
                }
            }
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(text = stringResource(R.string.detail_description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = detail.detail?.ifBlank { null } ?: detail.description, style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionSheet(
    state: ProductDetailUiState,
    onSelect: (Long, Long) -> Unit,
    onQuantity: (Int) -> Unit,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
) {
    val detail = state.detail ?: return
    val sku = state.selectedSku
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = SHEET_BOTTOM_SPACE)) {
        Text(text = stringResource(R.string.option_sheet_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        detail.optionGroups.forEach { group ->
            Text(text = group.name, style = MaterialTheme.typography.labelLarge, color = ModuGrey)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.values.forEach { value ->
                    val available = ProductDetailUiState.isValueAvailable(detail, state.selected, group.id, value.id)
                    FilterChip(
                        selected = state.selected[group.id] == value.id,
                        onClick = { onSelect(group.id, value.id) },
                        enabled = available,
                        label = { Text(if (available) value.name else "${value.name} (${stringResource(R.string.sold_out)})") },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        if (sku == null) {
            Text(text = stringResource(R.string.option_choose_all), style = MaterialTheme.typography.bodyMedium, color = ModuGrey)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = sku.optionLabel.ifBlank { detail.name }, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sku.extraPrice > 0) Text(text = stringResource(R.string.option_extra_price, formatPrice(sku.extraPrice)), style = MaterialTheme.typography.bodySmall, color = BrandRed)
                        Text(
                            text = if (sku.soldOut) stringResource(R.string.sold_out) else stringResource(R.string.option_stock_left, sku.stock),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sku.soldOut) BrandRed else ModuGrey,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = { onQuantity(-1) }, enabled = state.quantity > 1, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Remove, contentDescription = null) }
                    Text(text = state.quantity.toString(), modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedIconButton(onClick = { onQuantity(1) }, enabled = state.quantity < sku.stock, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Add, contentDescription = null) }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.option_total), style = MaterialTheme.typography.bodyMedium, color = ModuGrey, modifier = Modifier.weight(1f))
            Text(text = stringResource(R.string.price_format, formatPrice(state.totalPrice)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        val ready = sku != null && !sku.soldOut && !state.working
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onAddToCart, enabled = ready, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.option_add_to_cart))
            }
            Button(onClick = onBuyNow, enabled = ready, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.option_buy_now))
            }
        }
        // 바텀시트 다이얼로그는 시스템 내비게이션 바 인셋을 못 받는 기기가 있다(Flip3 3버튼 바).
        // 버튼이 그 밑으로 깔리면 탭이 앱에 오지 않으므로 여백을 넉넉히 둔다.
    }
}

private val SHEET_BOTTOM_SPACE = 16.dp
