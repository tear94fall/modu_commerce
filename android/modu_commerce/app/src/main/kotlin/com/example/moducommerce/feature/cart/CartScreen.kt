package com.example.moducommerce.feature.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.model.CartItem
import com.example.moducommerce.core.ui.components.CommerceTopBar
import com.example.moducommerce.core.ui.components.EmptyBox
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.components.ProductImage
import com.example.moducommerce.core.ui.theme.BrandRed
import com.example.moducommerce.core.ui.theme.ModuGrey
import com.example.moducommerce.core.util.formatPrice

@Composable
fun CartScreen(
    onBack: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onCheckout: (List<Long>) -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.messageRes) {
        val res = state.messageRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(res))
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = { CommerceTopBar(title = stringResource(R.string.cart_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.items.isEmpty()) return@Scaffold
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.cart_total, state.totalQuantity), style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                        Text(text = stringResource(R.string.price_format, formatPrice(state.totalAmount)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { onCheckout(state.orderable.map { it.id }) }, enabled = state.orderable.isNotEmpty(), modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.cart_checkout))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.error -> ErrorBox(message = stringResource(R.string.products_failed), modifier = Modifier.padding(padding), onRetry = viewModel::load)
            state.items.isEmpty() -> EmptyBox(message = stringResource(R.string.cart_empty), modifier = Modifier.padding(padding))
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.items, key = { it.id }) { item ->
                    CartRow(item = item, onOpen = { onOpenProduct(item.productId) }, onQuantity = { viewModel.changeQuantity(item.id, it) }, onRemove = { viewModel.remove(item.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CartRow(item: CartItem, onOpen: () -> Unit, onQuantity: (Int) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).alpha(if (item.orderable) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductImage(url = item.imageUrl, modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.productName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 4.dp))
            if (item.optionLabel.isNotBlank()) {
                Text(text = item.optionLabel, style = MaterialTheme.typography.bodySmall, color = ModuGrey)
            }
            if (!item.available) {
                Text(text = stringResource(R.string.cart_unavailable), style = MaterialTheme.typography.bodySmall, color = BrandRed)
            } else if (item.stock < item.quantity) {
                Text(text = stringResource(R.string.cart_stock_short, item.stock), style = MaterialTheme.typography.bodySmall, color = BrandRed)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = { onQuantity(-1) }, enabled = item.quantity > 1, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Remove, contentDescription = null) }
                    Text(text = item.quantity.toString(), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                    OutlinedIconButton(onClick = { onQuantity(1) }, enabled = item.quantity < CartViewModel.MAX_QUANTITY, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Add, contentDescription = null) }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.price_format, formatPrice(item.lineAmount)), fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.cart_remove), color = ModuGrey) }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.action_back).let { "상품 보기" }, color = ModuGrey) }
            }
        }
    }
}
