package com.example.moducommerce.core.push

/**
 * 푸시를 눌러 열 웹 경로와 (캠페인 발송이면) 캠페인 id. 테스트 발송은 campaignId 가 없다.
 * 경로는 허용 목록만 받는다(웹 `bridge/web.ts` 와 같은 목록): `/`, `/coupons`, `/products/<숫자>`, `/promotions/<숫자>`.
 */
data class PushLink private constructor(val path: String, val campaignId: Long?) {

    /** WebView 가 아직 앱을 띄우지 않았을 때 바로 여는 주소. */
    fun webUrl(webBase: String): String = webBase.trimEnd('/') + path

    /** 이미 떠 있는 웹 앱에서 react-router 로 이동시키는 스크립트. 경로는 허용 목록([a-z0-9/])이라 따옴표 탈출이 필요 없다. */
    fun navigateJs(): String = "window.ModuWeb && window.ModuWeb.navigate('$path')"

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_CAMPAIGN_ID = "campaignId"

        private val ALLOWED_PATH = Regex("^/(coupons|products/\\d+|promotions/\\d+)?$")

        fun isAllowed(path: String?): Boolean = path != null && ALLOWED_PATH.matches(path)

        /**
         * 인텐트 엑스트라(우리 알림의 PendingIntent, 또는 앱이 백그라운드일 때 FCM 이 넣어 주는 data 문자열)에서 만든다.
         * 허용되지 않은 경로면 null. campaignId 는 숫자가 아니면 버린다(이동은 한다).
         */
        fun from(path: String?, campaignId: String?): PushLink? {
            if (!isAllowed(path)) return null
            return PushLink(path!!, campaignId?.trim()?.toLongOrNull()?.takeIf { it > 0 })
        }
    }
}
