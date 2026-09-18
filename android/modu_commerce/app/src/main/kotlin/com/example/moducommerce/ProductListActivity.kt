package com.example.moducommerce

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.gson.JsonParser
import java.util.concurrent.Executors

/**
 * 로그인 이후의 첫 화면. 로그인한 계정을 한 줄 보여 주고 commerce-service 의 상품 목록을 띄운다.
 * 앱바에서 검색하고, 카드를 누르면 상세로 간다. 모든 호출은 TokenRefresher 를 거쳐 401 이면 한 번 갱신하고,
 * 그래도 안 되면 토큰을 비우고 로그인 화면으로 돌려보낸다.
 */
class ProductListActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var account: TextView
    private lateinit var status: TextView
    private lateinit var tokenStore: TokenStore
    private lateinit var auth: ModuAuthClient
    private lateinit var refresher: TokenRefresher
    private lateinit var commerce: CommerceApiClient
    private lateinit var googleClient: GoogleSignInClient
    private val adapter = ProductAdapter { openDetail(it) }

    /** 지금 목록이 보여 주는 검색어. null 이면 전체. 상세에서 돌아와 다시 불러올 때도 쓴다. */
    private var currentQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)
        setTitle(R.string.app_name)

        account = findViewById(R.id.products_account)
        status = findViewById(R.id.products_status)
        findViewById<RecyclerView>(R.id.products).let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
        }

        tokenStore = TokenStore(this)
        auth = ModuAuthClient(BuildConfig.API_BASE_URL)
        refresher = TokenRefresher(
            loadAccess = { tokenStore.access() },
            loadRefresh = { tokenStore.refresh() },
            renew = { auth.refresh(it) },
            save = { tokenStore.save(it) },
        )
        commerce = CommerceApiClient(BuildConfig.COMMERCE_API_URL)
        googleClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        if (tokenStore.access() == null) backToLogin(sessionExpired = true) else showMe()
    }

    /** 상세에서 돌아오면 그 사이 백오피스에서 바뀐 내용을 반영하도록 지금 검색어로 다시 불러온다. */
    override fun onRestart() {
        super.onRestart()
        if (tokenStore.access() != null) loadProducts()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_products, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.products_search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search(query)
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean = false
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                if (currentQuery != null) search(null)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_logout -> {
            logout()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** /userinfo 로 이름을 받아 보여준 뒤 상품을 불러온다. */
    private fun showMe() {
        showStatus(getString(R.string.products_loading))
        io.execute {
            try {
                val body = refresher.withFreshAccess { auth.userinfo(it) }
                val me = JsonParser.parseString(body).asJsonObject
                val name = me.get("name")?.asString ?: ""
                val email = me.get("email")?.asString ?: ""
                main.post {
                    account.text = getString(R.string.products_signed_in_as, name, email)
                    loadProducts()
                }
            } catch (e: Exception) {
                Log.e(TAG, "userinfo failed", e)
                main.post { onFailure(e) }
            }
        }
    }

    private fun search(query: String?) {
        currentQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        showStatus(getString(R.string.products_loading))
        loadProducts()
    }

    /** commerce-service 에서 상품 목록을 받아 보여준다. 같은 모두 계정 토큰으로 부른다. */
    private fun loadProducts() {
        val query = currentQuery
        io.execute {
            try {
                val list = refresher.withFreshAccess { commerce.products(it, query) }
                // 응답이 오는 사이 검색어가 바뀌었으면 옛 결과로 덮지 않는다.
                main.post { if (query == currentQuery) showProducts(list, query) }
            } catch (e: Exception) {
                Log.e(TAG, "products failed", e)
                main.post { onFailure(e) }
            }
        }
    }

    private fun showProducts(list: List<Product>, query: String?) {
        adapter.submit(list)
        if (list.isNotEmpty()) {
            hideStatus()
            return
        }
        showStatus(
            when (val empty = EmptyState.of(query)) {
                EmptyState.NoProducts -> getString(R.string.products_empty)
                is EmptyState.NoResults -> getString(R.string.products_no_results, empty.query)
            }
        )
    }

    private fun onFailure(e: Exception) {
        if (TokenRefresher.isSessionExpired(e)) {
            tokenStore.clear()
            backToLogin(sessionExpired = true)
        } else {
            showStatus(getString(R.string.products_failed, e.message))
        }
    }

    private fun openDetail(product: Product) {
        startActivity(Intent(this, ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.id))
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    private fun logout() {
        val refresh = tokenStore.refresh()
        tokenStore.clear()
        googleClient.signOut()
        backToLogin(sessionExpired = false)
        if (refresh == null) return
        io.execute {
            try {
                auth.revoke(refresh)
            } catch (e: Exception) {
                Log.w(TAG, "revoke failed: ${e.message}")
            }
        }
    }

    private fun backToLogin(sessionExpired: Boolean) {
        val intent = Intent(this, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, sessionExpired)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "ModuCommerce"
    }
}
