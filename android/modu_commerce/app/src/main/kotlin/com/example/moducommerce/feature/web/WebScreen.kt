package com.example.moducommerce.feature.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moducommerce.BuildConfig
import com.example.moducommerce.R
import com.example.moducommerce.core.network.ApiConfig
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.theme.BrandRed
import kotlinx.coroutines.delay

/**
 * 커머스 화면 전부를 여는 WebView. 상태바는 브랜드 레드로 칠하고 그 아래에 웹을 둔다(웹의 상단바 색과 이어진다).
 * 뒤로 가기는 웹 히스토리를 먼저 소비하고, 첫 화면이면 액티비티를 닫는다.
 *
 * 메인 문서를 못 열면 흰 오류 덮개 + 다시 시도. WebView 기본 오류 페이지는 덮개 아래에만 있고 보이지 않는다
 * (규칙은 [WebLoadState]). 인터넷이 없으면 오프라인 안내를 보이고, 연결이 돌아오면 알아서 다시 불러온다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(viewModel: WebViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connectivity = remember { context.getSystemService(ConnectivityManager::class.java) }
    var state by remember { mutableStateOf(WebLoadState()) }
    var canGoBack by remember { mutableStateOf(false) }
    // onPageFinished 의 JS 확인 결과가 늦게 와서 다음 로드의 상태를 덮지 않도록 로드마다 번호를 매긴다.
    var loadGeneration by remember { mutableStateOf(0) }

    val webView = remember {
        // 디버그 빌드는 chrome://inspect 로 웹을 들여다볼 수 있게 한다.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        WebView(context).apply {
            // AndroidView 의 기본은 wrap_content 라 WebView 가 높이를 "정해지지 않음" 으로 재고, 그러면 CSS vh 가 0 이 된다.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // 웹이 앱처럼 보이도록 오른쪽 스크롤바는 그리지 않는다.
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.White.toArgb())
            addJavascriptInterface(viewModel.bridge, ModuAppBridge.NAME)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    loadGeneration += 1
                    state = state.started()
                    canGoBack = view.canGoBack()
                    if (BuildConfig.DEBUG) Log.d(TAG, "started $url -> ${state.cover}")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    canGoBack = view.canGoBack()
                    val generation = loadGeneration
                    // 오류 콜백이 안 와도(같은 주소 재로드) 끝난 문서가 우리 앱인지 직접 확인한다.
                    view.evaluateJavascript(WebLoadState.APP_PAGE_CHECK_JS) { result ->
                        if (generation != loadGeneration) return@evaluateJavascript
                        state = state.finished(isAppPage = result == "true")
                        if (BuildConfig.DEBUG) Log.d(TAG, "finished $url app=$result -> ${state.cover}")
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    // 이미지 한 장 실패로 화면을 덮지 않는다. 메인 문서만 본다.
                    if (!request.isForMainFrame) return
                    state = state.mainFrameError()
                    if (BuildConfig.DEBUG) Log.d(TAG, "error ${error.errorCode} ${error.description} -> ${state.cover}")
                }
            }
            loadUrl(ApiConfig.WEB_URL)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface(ModuAppBridge.NAME)
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    /** 다시 시도(버튼·자동). 덮개는 그대로 두고 그 안에서 스피너만 돈다. */
    fun retry() {
        state = state.retry(online = connectivity.isOnline())
        if (state.shouldLoad) {
            webView.stopLoading()
            webView.loadUrl(ApiConfig.WEB_URL)
        }
    }

    // 로드가 끝없이 늘어지면(느린 연결, 콜백이 오지 않는 재로드) 멈추고 오류 덮개로 간다. 스피너가 영원히 돌지 않게.
    val waiting = state.loading || state.retrying
    LaunchedEffect(waiting, loadGeneration) {
        if (!waiting) return@LaunchedEffect
        delay(WebLoadState.LOAD_TIMEOUT_MS)
        if (state.loading || state.retrying) {
            webView.stopLoading()
            state = state.timedOut()
            if (BuildConfig.DEBUG) Log.d(TAG, "timed out -> ${state.cover}")
        }
    }

    // 오류·오프라인일 때만 네트워크를 지켜보다가, 끊겼던 연결이 돌아오면 다시 불러온다.
    // 등록 시점에 이미 연결이 있으면(서버만 안 되는 경우) 바로 재시도하지 않는다: 버튼으로만.
    DisposableEffect(state.canAutoRetry) {
        if (!state.canAutoRetry) return@DisposableEffect onDispose {}
        var hadNetwork = connectivity.isOnline()
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (hadNetwork) return
                    hadNetwork = true
                    webView.post { if (state.canAutoRetry) retry() }
                }

                override fun onLost(network: Network) {
                    hadNetwork = false
                }
            }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }

    // edge-to-edge 창이라 상태바 뒤는 우리가 칠한다: 웹 상단바와 같은 브랜드 레드. 내비게이션 바 위는 흰 바탕.
    Column(modifier = Modifier.fillMaxSize().background(Color.White).navigationBarsPadding()) {
        Spacer(modifier = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(BrandRed))
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            WebCoverBox(cover = state.cover, onRetry = ::retry)
        }
    }
}

/** WebView 위의 불투명한 흰 덮개. 로딩·재시도·오류·오프라인 모두 아래의 웹(기본 오류 페이지 포함)을 완전히 가린다. */
@Composable
private fun WebCoverBox(
    cover: WebCover,
    onRetry: () -> Unit,
) {
    if (cover == WebCover.NONE) return
    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        when (cover) {
            WebCover.LOADING -> CircularProgressIndicator()
            WebCover.RETRYING ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator()
                    Text(text = stringResource(R.string.web_retrying), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
            WebCover.ERROR -> ErrorBox(message = stringResource(R.string.web_failed), onRetry = onRetry)
            WebCover.OFFLINE -> ErrorBox(message = stringResource(R.string.web_offline), onRetry = onRetry)
            WebCover.NONE -> Unit
        }
    }
}

private const val TAG = "WebScreen"

/** 인터넷을 쓸 수 있는 네트워크가 잡혀 있는가(비행기 모드·연결 없음이면 false). 서버 도달 여부까지는 모른다. */
private fun ConnectivityManager.isOnline(): Boolean = getNetworkCapabilities(activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
