package com.example.moducommerce

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.gson.JsonParser
import java.util.concurrent.Executors

/**
 * 모두 계정 SSO 샘플.
 * 1) "모두 계정으로 로그인": 모두의 채팅 앱에 1회용 코드를 요청(PKCE) → auth-service 에서 이 앱 토큰으로 교환.
 * 2) "Google 로 로그인": 채팅 앱이 없을 때의 폴백. 같은 구글 계정이면 같은 모두 계정이다.
 */
class MainActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var products: ListView
    private lateinit var tokenStore: TokenStore
    private lateinit var auth: ModuAuthClient
    private lateinit var commerce: CommerceApiClient
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
            if (idToken == null) status.text = "Google 이 ID 토큰을 주지 않았습니다." else exchangeGoogle(idToken)
        } catch (e: ApiException) {
            status.text = "Google 로그인 실패 (코드 ${e.statusCode}). 콘솔에 이 앱의 패키지/SHA-1 이 등록돼 있어야 합니다."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        products = findViewById(R.id.products)
        tokenStore = TokenStore(this)
        auth = ModuAuthClient(BuildConfig.API_BASE_URL)
        commerce = CommerceApiClient(BuildConfig.COMMERCE_API_URL)
        googleClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        findViewById<Button>(R.id.login_with_modu).setOnClickListener { loginViaChatApp() }
        findViewById<Button>(R.id.login_with_google).setOnClickListener { googleLauncher.launch(googleClient.signInIntent) }
        findViewById<Button>(R.id.logout).setOnClickListener { logout() }

        if (tokenStore.access() != null) showMe()
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
            status.text = "모두의 채팅 앱이 없습니다. Google 로 로그인해 주세요."
        }
    }

    private fun exchangeSsoCode(code: String) {
        val verifier = pendingVerifier ?: return
        status.text = "코드를 토큰으로 바꾸는 중…"
        io.execute {
            try {
                tokenStore.save(auth.exchangeSsoCode(code, verifier))
                main.post { showMe() }
            } catch (e: Exception) {
                Log.e(TAG, "sso exchange failed", e)
                main.post { status.text = "토큰 교환 실패: ${e.message}" }
            }
        }
    }

    private fun exchangeGoogle(idToken: String) {
        status.text = "Google 토큰을 모두 토큰으로 바꾸는 중…"
        io.execute {
            try {
                tokenStore.save(auth.exchangeGoogleIdToken(idToken))
                main.post { showMe() }
            } catch (e: Exception) {
                Log.e(TAG, "google exchange failed", e)
                main.post { status.text = "토큰 교환 실패: ${e.message}" }
            }
        }
    }

    /** /userinfo 로 이름을 받아 보여준다. 401 이면 리프레시를 한 번 시도한다. */
    private fun showMe() {
        val access = tokenStore.access() ?: return
        io.execute {
            try {
                val body = try {
                    auth.userinfo(access)
                } catch (e: ModuAuthClient.AuthException) {
                    val refresh = tokenStore.refresh()
                    if (e.status != 401 || refresh == null) throw e
                    val renewed = auth.refresh(refresh)
                    tokenStore.save(renewed)
                    auth.userinfo(renewed.accessToken ?: throw e)
                }
                val me = JsonParser.parseString(body).asJsonObject
                val name = me.get("name")?.asString ?: ""
                val email = me.get("email")?.asString ?: ""
                main.post {
                    status.text = "로그인됨: $name ($email)"
                    loadProducts()
                }
            } catch (e: Exception) {
                Log.e(TAG, "userinfo failed", e)
                tokenStore.clear()
                main.post { status.text = "세션이 만료됐습니다. 다시 로그인해 주세요." }
            }
        }
    }

    private fun logout() {
        val refresh = tokenStore.refresh()
        tokenStore.clear()
        googleClient.signOut()
        status.text = "로그아웃됨"
        showProducts(emptyList())
        if (refresh == null) return
        io.execute {
            try {
                auth.revoke(refresh)
            } catch (e: Exception) {
                Log.w(TAG, "revoke failed: ${e.message}")
            }
        }
    }

    /** commerce-service 에서 상품 목록을 받아 보여준다. 같은 모두 계정 토큰으로 부른다. */
    private fun loadProducts() {
        val access = tokenStore.access() ?: return
        io.execute {
            try {
                val list = commerce.products(access)
                main.post { showProducts(list) }
            } catch (e: Exception) {
                Log.e(TAG, "products failed", e)
                main.post { status.append("\n상품 조회 실패: ${e.message}") }
            }
        }
    }

    private fun showProducts(list: List<Product>) {
        products.adapter = object : ArrayAdapter<Product>(this, android.R.layout.simple_list_item_2, android.R.id.text1, list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val item = getItem(position)
                view.findViewById<TextView>(android.R.id.text1).text = "${item?.name}  ${item?.priceLabel()}"
                view.findViewById<TextView>(android.R.id.text2).text = item?.description ?: ""
                return view
            }
        }
    }

    private fun describe(reason: String): String = when (reason) {
        "not_logged_in" -> "모두의 채팅에 먼저 로그인해 주세요."
        "denied" -> "채팅 앱에서 로그인을 거부했습니다."
        "caller_not_allowed" -> "이 앱은 채팅 앱의 허용 목록에 없습니다."
        "server_error" -> "코드 발급에 실패했습니다. 잠시 후 다시 시도해 주세요."
        else -> "로그인이 취소됐습니다 ($reason)"
    }

    companion object {
        private const val TAG = "ModuCommerce"
        private const val CHAT_PACKAGE = "com.example.modumessenger"
        private const val SSO_ACTION = "com.example.modumessenger.action.REQUEST_SSO_CODE"
    }
}
