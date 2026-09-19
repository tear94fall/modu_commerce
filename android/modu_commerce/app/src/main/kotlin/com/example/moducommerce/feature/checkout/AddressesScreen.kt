package com.example.moducommerce.feature.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.model.Address
import com.example.moducommerce.core.model.AddressInput
import com.example.moducommerce.core.network.ApiException
import com.example.moducommerce.core.ui.components.CommerceTopBar
import com.example.moducommerce.core.ui.components.EmptyBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.theme.BrandRed
import com.example.moducommerce.core.ui.theme.ModuGrey
import com.example.moducommerce.data.repository.OrderRepository
import com.example.moducommerce.feature.checkout.CheckoutViewModel.Companion.serverMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddressesUiState(
    val addresses: List<Address> = emptyList(),
    val loading: Boolean = true,
    val editing: Address? = null,
    val showForm: Boolean = false,
    val messageText: String? = null,
)

@HiltViewModel
class AddressesViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddressesUiState())
    val uiState: StateFlow<AddressesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            orderRepository.addresses().onSuccess { list -> _uiState.update { it.copy(addresses = list, loading = false) } }
                .onFailure { _uiState.update { it.copy(loading = false, messageText = "res:${R.string.products_failed}") } }
        }
    }

    fun openForm(address: Address? = null) = _uiState.update { it.copy(showForm = true, editing = address) }

    fun closeForm() = _uiState.update { it.copy(showForm = false, editing = null) }

    fun save(input: AddressInput) {
        val editing = _uiState.value.editing
        viewModelScope.launch {
            val result = if (editing == null) orderRepository.createAddress(input) else orderRepository.updateAddress(editing.id, input)
            result.onSuccess {
                _uiState.update { it.copy(showForm = false, editing = null) }
                load()
            }.onFailure { e -> _uiState.update { it.copy(messageText = (e as? ApiException)?.takeIf { it.code == 400 }?.serverMessage() ?: "res:${R.string.address_save_failed}") } }
        }
    }

    fun setDefault(id: Long) = viewModelScope.launch { orderRepository.setDefaultAddress(id).onSuccess { load() } }

    fun delete(id: Long) = viewModelScope.launch { orderRepository.deleteAddress(id).onSuccess { load() } }

    fun consumeMessage() = _uiState.update { it.copy(messageText = null) }
}

@Composable
fun AddressesScreen(onBack: () -> Unit, viewModel: AddressesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.messageText) {
        val text = state.messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resolveMessage(context, text))
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = { CommerceTopBar(title = stringResource(R.string.my_addresses), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Button(onClick = { viewModel.openForm() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.checkout_address_add)) }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.addresses.isEmpty() -> EmptyBox(message = stringResource(R.string.checkout_address_none), modifier = Modifier.padding(padding))
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.addresses, key = { it.id }) { address ->
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        AddressBlock(address)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!address.isDefault) TextButton(onClick = { viewModel.setDefault(address.id) }) { Text(stringResource(R.string.address_default), color = ModuGrey) }
                            TextButton(onClick = { viewModel.openForm(address) }) { Text("수정", color = ModuGrey) }
                            TextButton(onClick = { viewModel.delete(address.id) }) { Text(stringResource(R.string.cart_remove), color = BrandRed) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (state.showForm) {
        AddressFormDialog(initial = state.editing, onSave = viewModel::save, onDismiss = viewModel::closeForm)
    }
}
