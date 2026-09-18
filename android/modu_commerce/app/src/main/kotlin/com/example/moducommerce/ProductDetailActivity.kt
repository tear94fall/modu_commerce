package com.example.moducommerce

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.util.concurrent.Executors

/**
 * 상품 상세. 목록에서 id 만 받아 서버에서 최신 값을 다시 읽는다 —
 * 목록을 받은 뒤 백오피스에서 수정·삭제됐을 수 있다.
 */
class ProductDetailActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var content: View
    private lateinit var status: TextView
    private lateinit var tokenStore: TokenStore
    private lateinit var refresher: TokenRefresher
    private lateinit var commerce: CommerceApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)
        setTitle(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        content = findViewById(R.id.detail_content)
        status = findViewById(R.id.detail_status)
        tokenStore = TokenStore(this)
        val auth = ModuAuthClient(BuildConfig.API_BASE_URL)
        refresher = TokenRefresher(
            loadAccess = { tokenStore.access() },
            loadRefresh = { tokenStore.refresh() },
            renew = { auth.refresh(it) },
            save = { tokenStore.save(it) },
        )
        commerce = CommerceApiClient(BuildConfig.COMMERCE_API_URL)

        val id = intent.getLongExtra(EXTRA_PRODUCT_ID, -1L)
        if (id < 0) {
            finish()
            return
        }
        load(id)
    }

    /** 앱바 뒤로가기. 목록은 스택에 그대로 있으니 닫기만 한다. */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load(id: Long) {
        showStatus(getString(R.string.detail_loading))
        io.execute {
            try {
                val product = refresher.withFreshAccess { commerce.product(it, id) }
                main.post { show(product) }
            } catch (e: Exception) {
                Log.e(TAG, "product $id failed", e)
                main.post {
                    when {
                        TokenRefresher.isSessionExpired(e) -> expireSession()
                        e is ModuAuthClient.AuthException && e.status == 404 -> showStatus(getString(R.string.detail_not_found))
                        else -> showStatus(getString(R.string.detail_failed))
                    }
                }
            }
        }
    }

    private fun show(product: Product) {
        findViewById<TextView>(R.id.detail_name).text = product.name ?: ""
        findViewById<TextView>(R.id.detail_price).text = product.priceLabel()
        findViewById<TextView>(R.id.detail_description).apply {
            text = product.description ?: ""
            visibility = if (product.description.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val image = findViewById<ImageView>(R.id.detail_image)
        product.imageUrlOrNull()?.let { Glide.with(image).load(it).centerCrop().into(image) }

        status.visibility = View.GONE
        content.visibility = View.VISIBLE
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
        content.visibility = View.GONE
    }

    /** 목록도 같은 토큰을 쓰므로 스택을 통째로 비우고 로그인으로 간다. */
    private fun expireSession() {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "product_id"
        private const val TAG = "ModuCommerce"
    }
}
