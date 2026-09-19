package com.example.moducommerce.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.model.Address
import com.example.moducommerce.core.ui.components.BottomPanel
import com.example.moducommerce.core.ui.components.CommerceTopBar
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.components.ProductImage
import com.example.moducommerce.core.ui.theme.BrandRed
import com.example.moducommerce.core.ui.theme.ModuGrey
import com.example.moducommerce.core.ui.theme.ProductSurface
import com.example.moducommerce.core.util.formatPrice

/** "res:1234" 는 문자열 리소스 id, 그 밖은 서버 문구 그대로. */
fun resolveMessage(context: android.content.Context, text: String): String =
    if (text.startsWith("res:")) context.getString(text.removePrefix("res:").toInt()) else text

@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onOrdered: (Long) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.ordered.collect { onOrdered(it) } }
    LaunchedEffect(state.messageText) {
        val text = state.messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resolveMessage(context, text))
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = { CommerceTopBar(title = stringResource(R.string.checkout_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.loading || state.error) return@Scaffold
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.checkout_total), style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                        Text(text = stringResource(R.string.price_format, formatPrice(state.totalAmount)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = viewModel::pay, enabled = state.canPay, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.checkout_pay))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.error -> ErrorBox(message = stringResource(R.string.products_failed), modifier = Modifier.padding(padding), onRetry = viewModel::load)
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    SectionHeader(stringResource(R.string.checkout_address)) {
                        if (state.addresses.isNotEmpty()) {
                            OutlinedButton(onClick = viewModel::openPicker) { Text(stringResource(R.string.checkout_address_change)) }
                        }
                    }
                    val address = state.selectedAddress
                    if (address == null) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Text(text = stringResource(R.string.checkout_address_none), color = BrandRed, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = viewModel::openAddressForm) { Text(stringResource(R.string.checkout_address_add)) }
                        }
                    } else {
                        AddressBlock(address, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(thickness = 8.dp, color = ProductSurface)
                }
                item { SectionHeader(stringResource(R.string.checkout_items)) }
                items(state.lines, key = { it.skuId }) { line ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProductImage(url = line.imageUrl, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = line.productName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (line.optionLabel.isNotBlank()) Text(text = line.optionLabel, style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                            Text(text = "${formatPrice(line.unitPrice)}원 × ${line.quantity}", style = MaterialTheme.typography.bodySmall, color = ModuGrey)
                        }
                        Text(text = stringResource(R.string.price_format, formatPrice(line.lineAmount)), fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 8.dp, color = ProductSurface)
                    SectionHeader(stringResource(R.string.checkout_payment))
                    Text(text = stringResource(R.string.checkout_payment_mock), modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    BottomPanel(visible = state.showAddressPicker, onDismiss = viewModel::closePicker) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(text = stringResource(R.string.checkout_select_address), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            state.addresses.forEach { address ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectAddress(address.id) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = address.id == state.selectedAddressId, onClick = { viewModel.selectAddress(address.id) })
                    AddressBlock(address)
                }
            }
            OutlinedButton(onClick = viewModel::openAddressForm, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text(stringResource(R.string.checkout_address_add)) }
        }
    }
    if (state.showAddressForm) {
        AddressFormDialog(onSave = viewModel::saveAddress, onDismiss = viewModel::closeAddressForm)
    }
}

@Composable
fun AddressBlock(address: Address, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = address.recipient, fontWeight = FontWeight.Bold)
            if (address.isDefault) {
                Text(
                    text = stringResource(R.string.checkout_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandRed,
                    modifier = Modifier.background(ProductSurface, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(text = address.phone, style = MaterialTheme.typography.bodySmall, color = ModuGrey)
        Text(text = "(${address.zipCode}) ${address.fullAddress}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
