package com.example.moducommerce.feature.login

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.auth.SsoContract
import com.example.moducommerce.core.ui.components.BrandingHeader
import com.example.moducommerce.core.ui.theme.GoogleButtonStroke
import com.example.moducommerce.core.ui.theme.GoogleButtonText
import com.example.moducommerce.core.ui.theme.ModuGrey

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.loggedIn.collect { onLoggedIn() } }

    val ssoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.data?.getStringExtra(SsoContract.EXTRA_CODE)
        if (result.resultCode == Activity.RESULT_OK && code != null) {
            viewModel.onSsoCode(code)
        } else {
            viewModel.onSsoFailed(result.data?.getStringExtra(SsoContract.EXTRA_REASON))
        }
    }
    val google = rememberGoogleSignInHelper(
        onAccount = { viewModel.onGoogleIdToken(it.idToken) },
        onFailure = viewModel::onGoogleFailed,
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        BrandingHeader()
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = state.messageRes?.let { res -> state.messageArg?.let { stringResource(res, it) } ?: stringResource(res) } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = ModuGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        Button(
            onClick = {
                try {
                    ssoLauncher.launch(SsoContract.requestIntent(viewModel.newSsoChallenge()))
                } catch (e: ActivityNotFoundException) {
                    viewModel.onNoChatApp()
                }
            },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.login_with_modu)) }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { google.signIn() },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, GoogleButtonStroke),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoogleButtonText),
        ) { Text(stringResource(R.string.login_with_google)) }
        Spacer(modifier = Modifier.height(48.dp))
    }
}
