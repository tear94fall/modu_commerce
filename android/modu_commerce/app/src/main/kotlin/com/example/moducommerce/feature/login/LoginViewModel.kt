package com.example.moducommerce.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.auth.PkceUtil
import com.example.moducommerce.data.repository.AuthRepository
import com.example.moducommerce.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val working: Boolean = false,
    /** 안내 문구. [messageArg] 가 있으면 %1$d 자리에 넣는다(구글 상태 코드). */
    val messageRes: Int? = null,
    val messageArg: Int? = null,
)

/**
 * 1) 모두 계정 SSO: PKCE 검증자를 만들고 채팅 앱에 코드를 요청 → 코드+검증자를 이 앱 토큰으로 교환.
 * 2) Google 폴백: id 토큰을 모두 토큰으로 교환. 같은 구글 계정이면 같은 모두 계정이다.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(messageRes = if (savedStateHandle.get<Boolean>(Routes.ARG_EXPIRED) == true) R.string.login_session_expired else null),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _loggedIn = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val loggedIn: SharedFlow<Unit> = _loggedIn.asSharedFlow()

    /** 채팅 앱에 보낸 요청의 PKCE 검증자. 결과가 돌아올 때까지 든다. */
    private var pendingVerifier: String? = null

    /** 채팅 앱에 보낼 code_challenge. 부를 때마다 새 검증자를 만든다. */
    fun newSsoChallenge(): String {
        val verifier = PkceUtil.generateVerifier()
        pendingVerifier = verifier
        return PkceUtil.challenge(verifier)
    }

    fun onSsoCode(code: String) {
        val verifier = pendingVerifier ?: return
        pendingVerifier = null
        exchange { authRepository.loginWithSsoCode(code, verifier) }
    }

    fun onSsoFailed(reason: String?) {
        pendingVerifier = null
        val res = when (reason) {
            "not_logged_in" -> R.string.sso_not_logged_in
            "denied" -> R.string.sso_denied
            "caller_not_allowed" -> R.string.sso_caller_not_allowed
            "server_error" -> R.string.sso_server_error
            else -> R.string.sso_cancelled
        }
        _uiState.update { it.copy(messageRes = res, messageArg = null) }
    }

    fun onNoChatApp() = _uiState.update { it.copy(messageRes = R.string.login_no_chat_app, messageArg = null) }

    fun onGoogleIdToken(idToken: String?) {
        if (idToken == null) {
            _uiState.update { it.copy(messageRes = R.string.login_no_google_id_token, messageArg = null) }
            return
        }
        exchange { authRepository.loginWithGoogle(idToken) }
    }

    fun onGoogleFailed(statusCode: Int?) =
        _uiState.update { it.copy(messageRes = R.string.login_google_failed, messageArg = statusCode ?: -1) }

    private fun exchange(block: suspend () -> Result<*>) {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, messageRes = R.string.login_exchanging, messageArg = null) }
        viewModelScope.launch {
            block()
                .onSuccess {
                    _uiState.update { it.copy(working = false, messageRes = null) }
                    _loggedIn.tryEmit(Unit)
                }
                .onFailure { _uiState.update { it.copy(working = false, messageRes = R.string.login_exchange_failed, messageArg = null) } }
        }
    }
}
