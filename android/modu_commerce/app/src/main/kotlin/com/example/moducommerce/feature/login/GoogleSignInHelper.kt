package com.example.moducommerce.feature.login

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.moducommerce.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException as GmsApiException

/** 구글 로그인(채팅 앱이 없을 때의 폴백). 옵션은 모두의 채팅과 같다. */
class GoogleSignInHelper internal constructor(
    private val client: GoogleSignInClient,
    private val launch: () -> Unit,
) {
    fun signIn() = launch()

    companion object {
        fun client(context: Context): GoogleSignInClient {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            return GoogleSignIn.getClient(context, options)
        }

        fun signOut(context: Context) {
            client(context).signOut()
        }

        fun statusCodeOf(error: Throwable?): Int? = (error as? GmsApiException)?.statusCode
    }
}

@Composable
fun rememberGoogleSignInHelper(
    onAccount: (GoogleSignInAccount) -> Unit,
    onFailure: (Int?) -> Unit,
): GoogleSignInHelper {
    val context = LocalContext.current
    val client = remember(context) { GoogleSignInHelper.client(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching { task.getResult(GmsApiException::class.java) }
            .onSuccess(onAccount)
            .onFailure { error -> onFailure(GoogleSignInHelper.statusCodeOf(error)) }
    }
    return remember(client, launcher) { GoogleSignInHelper(client) { launcher.launch(client.signInIntent) } }
}
