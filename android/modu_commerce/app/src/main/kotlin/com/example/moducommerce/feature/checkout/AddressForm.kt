package com.example.moducommerce.feature.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.moducommerce.R
import com.example.moducommerce.core.model.Address
import com.example.moducommerce.core.model.AddressInput

/** 배송지 입력 다이얼로그. 주문서와 배송지 관리가 같이 쓴다. 형식 검증은 서버가 하고 400 문구를 그대로 보여 준다. */
@Composable
fun AddressFormDialog(
    initial: Address? = null,
    onSave: (AddressInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var recipient by rememberSaveable { mutableStateOf(initial?.recipient.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(initial?.phone.orEmpty()) }
    var zip by rememberSaveable { mutableStateOf(initial?.zipCode.orEmpty()) }
    var line1 by rememberSaveable { mutableStateOf(initial?.address1.orEmpty()) }
    var line2 by rememberSaveable { mutableStateOf(initial?.address2.orEmpty()) }
    var isDefault by rememberSaveable { mutableStateOf(initial?.isDefault ?: false) }
    val valid = recipient.isNotBlank() && phone.isNotBlank() && zip.length == 5 && line1.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.address_form_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = recipient, onValueChange = { recipient = it }, label = { Text(stringResource(R.string.address_recipient)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.address_phone)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = zip, onValueChange = { zip = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.address_zip)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = line1, onValueChange = { line1 = it }, label = { Text(stringResource(R.string.address_line1)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = line2, onValueChange = { line2 = it }, label = { Text(stringResource(R.string.address_line2)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Text(stringResource(R.string.address_default))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(AddressInput(recipient.trim(), phone.trim(), zip, line1.trim(), line2.trim().ifBlank { null }, isDefault)) },
            ) { Text(stringResource(R.string.address_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.my_cancel)) } },
    )
}
