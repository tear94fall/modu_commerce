package com.example.moducommerce.feature.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.model.OrderDetail
import com.example.moducommerce.core.model.OrderStatus
import com.example.moducommerce.core.model.OrderSummary
import com.example.moducommerce.core.ui.components.CommerceTopBar
import com.example.moducommerce.core.ui.components.EmptyBox
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.components.ProductImage
import com.example.moducommerce.core.ui.theme.BrandRed
import com.example.moducommerce.core.ui.theme.ModuGrey
import com.example.moducommerce.core.ui.theme.ProductSurface
import com.example.moducommerce.core.util.formatDateTime
import com.example.moducommerce.core.util.formatPrice
import com.example.moducommerce.feature.checkout.resolveMessage

fun OrderStatus.labelRes(): Int = when (this) {
    OrderStatus.PAID -> R.string.order_status_paid
    OrderStatus.SHIPPING -> R.string.order_status_shipping
    OrderStatus.DELIVERED -> R.string.order_status_delivered
    OrderStatus.CANCELLED -> R.string.order_status_cancelled
    OrderStatus.UNKNOWN -> R.string.order_status_unknown
}

@Composable
private fun StatusText(status: OrderStatus) {
    Text(
        text = stringResource(status.labelRes()),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (status == OrderStatus.CANCELLED) ModuGrey else BrandRed,
    )
}

@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= state.orders.size - 3 } }
    LaunchedEffect(nearEnd, state.orders.size) { if (nearEnd) viewModel.loadMore() }

    Scaffold(topBar = { CommerceTopBar(title = stringResource(R.string.orders_title), onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.error -> ErrorBox(message = stringResource(R.string.products_failed), modifier = Modifier.padding(padding), onRetry = viewModel::load)
            state.orders.isEmpty() -> EmptyBox(message = stringResource(R.string.orders_empty), modifier = Modifier.padding(padding))
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.orders, key = { it.id }) { order ->
                    OrderRow(order = order, onClick = { onOpenOrder(order.id) })
                    HorizontalDivider()
                }
                if (state.loadingMore) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: OrderSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatDateTime(order.createdAt), style = MaterialTheme.typography.bodySmall, color = ModuGrey, modifier = Modifier.weight(1f))
            StatusText(order.status)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(url = order.firstImageUrl, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
            Column(modifier = Modifier.weight(1f)) {
                val title = if (order.itemCount > 1) "${order.firstItemName} ${stringResource(R.string.orders_more_items, order.itemCount - 1)}" else order.firstItemName
                Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = stringResource(R.string.price_format, formatPrice(order.totalAmount)), fontWeight = FontWeight.Bold)
                Text(text = order.orderNo, style = MaterialTheme.typography.bodySmall, color = ModuGrey)
            }
        }
    }
}

@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(state.messageText) {
        val text = state.messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resolveMessage(context, text))
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = { CommerceTopBar(title = stringResource(R.string.order_detail_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.notFound -> EmptyBox(message = stringResource(R.string.detail_not_found), modifier = Modifier.padding(padding))
            state.error -> ErrorBox(message = stringResource(R.string.products_failed), modifier = Modifier.padding(padding), onRetry = viewModel::load)
            else -> state.order?.let { order -> OrderBody(order = order, working = state.working, onOpenProduct = onOpenProduct, onCancel = { confirmCancel = true }, modifier = Modifier.padding(padding)) }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.order_cancel)) },
            text = { Text(stringResource(R.string.order_cancel_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) { Text(stringResource(R.string.order_cancel), color = BrandRed) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.my_cancel)) } },
        )
    }
}

@Composable
private fun OrderBody(order: OrderDetail, working: Boolean, onOpenProduct: (Long) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = stringResource(R.string.order_no, order.orderNo), style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusText(order.status)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = formatDateTime(order.paidAt), style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                }
                order.cancelledAt?.let {
                    Text(text = "${stringResource(R.string.order_status_cancelled)} ${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                }
            }
            HorizontalDivider(thickness = 8.dp, color = ProductSurface)
        }
        item {
            Text(text = stringResource(R.string.order_items), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        items(order.items, key = { it.id }) { item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenProduct(item.productId) }.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProductImage(url = item.imageUrl, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.productName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.optionLabel.isNotBlank()) Text(text = item.optionLabel, style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                    Text(text = "${formatPrice(item.unitPrice)}원 × ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                }
                Text(text = stringResource(R.string.price_format, formatPrice(item.lineAmount)), fontWeight = FontWeight.Bold)
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 8.dp, color = ProductSurface)
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(text = stringResource(R.string.order_recipient), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "${order.recipient} · ${order.phone}")
                Text(text = "(${order.zipCode}) ${order.fullAddress}", style = MaterialTheme.typography.bodyMedium, color = ModuGrey)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.order_payment), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.checkout_payment_mock), modifier = Modifier.weight(1f), color = ModuGrey)
                    Text(text = stringResource(R.string.price_format, formatPrice(order.totalAmount)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                if (order.cancellable) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(onClick = onCancel, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.order_cancel), color = BrandRed) }
                }
                Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}
