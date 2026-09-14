package com.example.moducommerce

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

/**
 * 로그인 화면. 모두 계정 SSO 샘플.
 * 1) "모두 계정으로 로그인": 모두의 채팅 앱에 1회용 코드를 요청(PKCE) → auth-service 에서 이 앱 토큰으로 교환.
 * 2) "Google 로 로그인": 채팅 앱이 없을 때의 폴백. 같은 구글 계정이면 같은 모두 계정이다.
 * 토큰을 받으면 상품 목록으로 넘어가고 이 화면은 스택에서 빠진다.
 */
class LoginActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var tokenStore: TokenStore
    private lateinit var auth: ModuAuthClient
    private lateinit var googleClient: GoogleSignInClient
    private var pendingVerifier: String? = null

    private val ssoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val code = data?.getStringExtra("code")
        if (result.resultCode == RESULT_OK && code != null) {
            exchangeSsoCode(code)
        } else {
            status.text = describe(data?.getStringExtra("reason") ?: "cancelled")
        }
    }

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken == null) {
                status.setText(R.string.login_no_google_id_token)
            } else {
                exchangeGoogle(idToken)
            }
        } catch (e: ApiException) {
            status.text = getString(R.string.login_google_failed, e.statusCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        status = findViewById(R.id.login_status)
        tokenStore = TokenStore(this)
        auth = ModuAuthClient(BuildConfig.API_BASE_URL)
        googleClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        findViewById<MaterialButton>(R.id.login_with_modu).setOnClickListener { loginViaChatApp() }
        findViewById<MaterialButton>(R.id.login_with_google).setOnClickListener {
            googleLauncher.launch(googleClient.signInIntent)
        }

        // 이전 화면이 세션 만료로 돌려보낸 경우 이유를 보여 준다.
        if (intent.getBooleanExtra(EXTRA_SESSION_EXPIRED, false)) status.setText(R.string.login_session_expired)
    }

    /** PKCE 를 만들고 채팅 앱의 SSO 액티비티를 부른다. 채팅 앱이 없으면 구글 버튼으로 유도한다. */
    private fun loginViaChatApp() {
        val verifier = PkceUtil.generateVerifier()
        pendingVerifier = verifier
        val intent = Intent(SSO_ACTION).apply {
            setPackage(CHAT_PACKAGE)
            putExtra("client_id", ModuAuthClient.CLIENT_ID)
            putExtra("code_challenge", PkceUtil.challenge(verifier))
            putExtra("code_challenge_method", "S256")
        }
        try {
            ssoLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            status.setText(R.string.login_no_chat_app)
        }
    }

    private fun exchangeSsoCode(code: String) {
        val verifier = pendingVerifier ?: return
        status.setText(R.string.login_exchanging_sso)
        io.execute {
            try {
                tokenStore.save(auth.exchangeSsoCode(code, verifier))
                main.post { goToProducts() }
            } catch (e: Exception) {
                Log.e(TAG, "sso exchange failed", e)
                main.post { status.text = getString(R.string.login_exchange_failed, e.message) }
            }
        }
    }

    private fun exchangeGoogle(idToken: String) {
        status.setText(R.string.login_exchanging_google)
        io.execute {
            try {
                tokenStore.save(auth.exchangeGoogleIdToken(idToken))
                main.post { goToProducts() }
            } catch (e: Exception) {
                Log.e(TAG, "google exchange failed", e)
                main.post { status.text = getString(R.string.login_exchange_failed, e.message) }
            }
        }
    }

    private fun goToProducts() {
        startActivity(Intent(this, ProductListActivity::class.java))
        finish()
    }

    private fun describe(reason: String): String = when (reason) {
        "not_logged_in" -> getString(R.string.sso_not_logged_in)
        "denied" -> getString(R.string.sso_denied)
        "caller_not_allowed" -> getString(R.string.sso_caller_not_allowed)
        "server_error" -> getString(R.string.sso_server_error)
        else -> getString(R.string.sso_cancelled, reason)
    }

    companion object {
        private const val TAG = "ModuCommerce"
        private const val CHAT_PACKAGE = "com.example.modumessenger"
        private const val SSO_ACTION = "com.example.modumessenger.action.REQUEST_SSO_CODE"

        /** 상품 목록이 세션 만료로 돌려보낼 때 쓰는 플래그. */
        const val EXTRA_SESSION_EXPIRED = "session_expired"
    }
}
