package com.example.moducommerce.feature.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.core.model.UserProfile
import com.example.moducommerce.core.session.SessionStore
import com.example.moducommerce.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyViewModel @Inject constructor(
    sessionStore: SessionStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = sessionStore.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    /** 로그아웃이 끝나면 [SessionEvents.loggedOut] 이 나가고 MainActivity 가 로그인 화면으로 보낸다. */
    fun logout() {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            authRepository.logout()
            _working.value = false
        }
    }
}
