package com.example.moducommerce.feature.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moducommerce.BuildConfig
import com.example.moducommerce.R
import com.example.moducommerce.core.network.ApiConfig
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.theme.BrandRed

/**
 * 커머스 화면 전부를 여는 WebView. 상태바는 브랜드 레드로 칠하고 그 아래에 웹을 둔다(웹의 상단바 색과 이어진다).
 * 뒤로 가기는 웹 히스토리를 먼저 소비하고, 첫 화면이면 액티비티를 닫는다. 메인 문서를 못 열면 오류 화면 + 다시 시도.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(viewModel: WebViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember {
        // 디버그 빌드는 chrome://inspect 로 웹을 들여다볼 수 있게 한다.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        WebView(context).apply {
            // AndroidView 의 기본은 wrap_content 라 WebView 가 높이를 "정해지지 않음" 으로 재고, 그러면 CSS vh 가 0 이 된다.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.White.toArgb())
            addJavascriptInterface(viewModel.bridge, ModuAppBridge.NAME)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    loading = true
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    canGoBack = view.canGoBack()
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    // 이미지 한 장 실패로 화면을 덮지 않는다. 메인 문서만 본다.
                    if (request.isForMainFrame) failed = true
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

    // edge-to-edge 창이라 상태바 뒤는 우리가 칠한다: 웹 상단바와 같은 브랜드 레드. 내비게이션 바 위는 흰 바탕.
    Column(modifier = Modifier.fillMaxSize().background(Color.White).navigationBarsPadding()) {
        Spacer(modifier = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(BrandRed))
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (failed) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    ErrorBox(
                        message = stringResource(R.string.web_failed),
                        onRetry = {
                            failed = false
                            webView.loadUrl(ApiConfig.WEB_URL)
                        },
                    )
                }
            } else if (loading) {
                LoadingBox()
            }
        }
    }
}
