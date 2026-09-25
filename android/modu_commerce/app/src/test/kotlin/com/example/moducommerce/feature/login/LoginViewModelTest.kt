package com.example.moducommerce.feature.login

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.moducommerce.R
import com.example.moducommerce.core.auth.PkceUtil
import com.example.moducommerce.core.model.UserProfile
import com.example.moducommerce.data.repository.AuthRepository
import com.example.moducommerce.navigation.Routes
import com.example.moducommerce.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FakeAuthRepository : AuthRepository {
        val ssoCalls = mutableListOf<Pair<String, String>>()
        val googleCalls = mutableListOf<String>()
        var result: Result<UserProfile> = Result.success(UserProfile("임", "a@b.c", ""))
        override suspend fun loginWithSsoCode(code: String, codeVerifier: String): Result<UserProfile> {
            ssoCalls += code to codeVerifier
            return result
        }
        override suspend fun loginWithGoogle(idToken: String): Result<UserProfile> {
            googleCalls += idToken
            return result
        }
        override suspend fun logout() = Unit
    }

    private val auth = FakeAuthRepository()

    private fun viewModel(expired: Boolean = false) = LoginViewModel(SavedStateHandle(mapOf(Routes.ARG_EXPIRED to expired)), auth)

    @Test
    fun `SSO 코드는 같은 요청의 검증자와 함께 교환되고 성공하면 loggedIn 이 나간다`() = runTest {
        val vm = viewModel()
        val challenge = vm.newSsoChallenge()

        vm.loggedIn.test {
            vm.onSsoCode("code-1")
            awaitItem()
            val (code, verifier) = auth.ssoCalls.single()
            assertEquals("code-1", code)
            assertEquals("검증자에서 만든 challenge 가 채팅 앱에 갔어야 한다", challenge, PkceUtil.challenge(verifier))
            assertFalse(vm.uiState.value.working)
            assertNull(vm.uiState.value.messageRes)
        }
    }

    @Test
    fun `검증자 없이 온 코드는 무시하고 취소 사유는 문구로 바꾼다`() = runTest {
        val vm = viewModel()
        vm.onSsoCode("stray")
        assertEquals(0, auth.ssoCalls.size)

        vm.onSsoFailed("not_logged_in")
        assertEquals(R.string.sso_not_logged_in, vm.uiState.value.messageRes)
        vm.onSsoFailed(null)
        assertEquals(R.string.sso_cancelled, vm.uiState.value.messageRes)
    }

    @Test
    fun `교환 실패는 안내만 하고 세션 만료로 들어오면 첫 문구가 만료 안내다`() = runTest {
        auth.result = Result.failure(RuntimeException("500"))
        val vm = viewModel(expired = true)
        assertEquals(R.string.login_session_expired, vm.uiState.value.messageRes)

        vm.onGoogleIdToken("id-token")
        assertEquals(listOf("id-token"), auth.googleCalls)
        assertEquals(R.string.login_exchange_failed, vm.uiState.value.messageRes)

        vm.onGoogleIdToken(null)
        assertEquals(R.string.login_no_google_id_token, vm.uiState.value.messageRes)
        vm.onGoogleFailed(10)
        assertEquals(10, vm.uiState.value.messageArg)
    }
}
