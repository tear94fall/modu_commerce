package com.example.moducommerce.feature.web

/** WebView 위에 덮는 것. NONE 이면 웹이 그대로 보인다. */
enum class WebCover { NONE, LOADING, RETRYING, ERROR, OFFLINE }

/**
 * WebView 메인 문서 로드 상태. 목표는 WebView 기본 오류 페이지("웹페이지를 사용할 수 없음")가 한 번도 보이지 않는 것.
 *
 * - 로드 중에는 흰 덮개를 씌운다(투명 스피너면 직전의 기본 오류 페이지가 비친다).
 * - 오류 덮개는 재시도가 **실제로 성공할 때까지** 걷지 않는다. 재시도 중에는 같은 덮개 안에서 스피너만 돈다.
 * - 성공 판정은 오류 콜백만 믿지 않는다. 기본 오류 페이지는 주소가 앱 주소와 같아서 같은 주소를 다시 불러오면
 *   WebView 가 새로고침처럼 처리해 오류 콜백 없이 오류 페이지를 다시 그린다. 그래서 끝난 문서가 우리 웹 앱인지([finished] 의 isAppPage)도 본다.
 */
data class WebLoadState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val retrying: Boolean = false,
    val offline: Boolean = false,
    /** 지금 로드에서 메인 문서 오류 콜백이 왔는가. onPageStarted 마다 지운다. */
    private val pageError: Boolean = false,
) {
    fun started() = copy(loading = true, pageError = false)

    /**
     * 메인 문서 오류. 로드 완료를 기다리지 않고 바로 오류 덮개로 간다:
     * 실패한 같은 주소를 다시 불러오면 WebView 는 이 콜백만 주고 onPageFinished 는 주지 않는다.
     */
    fun mainFrameError() = copy(pageError = true, failed = true, retrying = false, loading = false)

    /** 로드가 [LOAD_TIMEOUT_MS] 안에 끝나지 않음(느리거나 불안정한 연결). 로드 중이 아니면 그대로. */
    fun timedOut(): WebLoadState = if (loading || retrying) copy(loading = false, failed = true, retrying = false, pageError = false) else this

    /** onPageFinished. [isAppPage] 는 끝난 문서가 우리 웹 앱(#root 가 있는 index.html)인지. */
    fun finished(isAppPage: Boolean): WebLoadState =
        if (!pageError && isAppPage) {
            WebLoadState(loading = false)
        } else {
            copy(loading = false, failed = true, retrying = false, pageError = false)
        }

    /** 다시 시도. 인터넷이 없으면 불러오지 않고 오프라인 안내로 바꾼다(연결되면 자동으로 다시 불러온다). */
    fun retry(online: Boolean) = if (online) copy(retrying = true, offline = false) else copy(offline = true, retrying = false)

    /** [retry] 직후 실제로 loadUrl 을 할지. */
    val shouldLoad: Boolean get() = retrying && !loading

    /** 네트워크가 돌아왔을 때 알아서 다시 불러와도 되는 상태. */
    val canAutoRetry: Boolean get() = (failed || offline) && !retrying

    val cover: WebCover
        get() =
            when {
                offline -> WebCover.OFFLINE
                retrying -> WebCover.RETRYING
                failed -> WebCover.ERROR
                loading -> WebCover.LOADING
                else -> WebCover.NONE
            }

    val coverVisible: Boolean get() = cover != WebCover.NONE

    companion object {
        /** 로드(첫 로드·재시도)가 이만큼 끝나지 않으면 오류로 본다. */
        const val LOAD_TIMEOUT_MS = 15_000L

        /** 끝난 문서가 우리 웹 앱인지. web/index.html 의 `<div id="root">` 는 기본 오류 페이지에 없다. */
        const val APP_PAGE_CHECK_JS = "document.getElementById('root') !== null"
    }
}
