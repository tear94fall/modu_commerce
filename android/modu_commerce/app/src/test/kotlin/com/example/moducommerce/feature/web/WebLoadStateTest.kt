package com.example.moducommerce.feature.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLoadStateTest {
    @Test
    fun `첫 로드가 성공하면 덮개를 걷는다`() {
        val s = WebLoadState().started().finished(isAppPage = true)
        assertFalse(s.coverVisible)
        assertEquals(WebCover.NONE, s.cover)
    }

    @Test
    fun `첫 로드 중에는 불투명한 로딩 덮개를 보인다`() {
        val s = WebLoadState().started()
        assertTrue(s.coverVisible)
        assertEquals(WebCover.LOADING, s.cover)
    }

    @Test
    fun `메인 문서 오류는 오류 덮개로 가고 로드가 끝나도 유지한다`() {
        val s = WebLoadState().started().mainFrameError().finished(isAppPage = false)
        assertEquals(WebCover.ERROR, s.cover)
    }

    @Test
    fun `오류 콜백이 없어도 끝난 문서가 우리 앱이 아니면 오류다`() {
        // 같은 주소를 다시 불러오면 WebView 가 오류 콜백 없이 기본 오류 페이지를 다시 그린다.
        val s = WebLoadState().started().finished(isAppPage = false)
        assertEquals(WebCover.ERROR, s.cover)
    }

    @Test
    fun `재시도 중에는 오류 덮개가 스피너로 바뀔 뿐 걷히지 않는다`() {
        val failed = WebLoadState().started().mainFrameError().finished(isAppPage = false)
        val retrying = failed.retry(online = true).started()
        assertEquals(WebCover.RETRYING, retrying.cover)
        assertTrue(retrying.coverVisible)
    }

    @Test
    fun `재시도가 다시 실패하면 오류 덮개로 돌아온다`() {
        val s =
            WebLoadState().started().mainFrameError().finished(isAppPage = false)
                .retry(online = true).started().finished(isAppPage = false)
        assertEquals(WebCover.ERROR, s.cover)
    }

    @Test
    fun `재시도가 성공하면 덮개를 걷는다`() {
        val s =
            WebLoadState().started().mainFrameError().finished(isAppPage = false)
                .retry(online = true).started().finished(isAppPage = true)
        assertEquals(WebCover.NONE, s.cover)
    }

    @Test
    fun `인터넷이 없으면 불러오지 않고 오프라인 안내를 보인다`() {
        val failed = WebLoadState().started().mainFrameError().finished(isAppPage = false)
        val s = failed.retry(online = false)
        assertEquals(WebCover.OFFLINE, s.cover)
        assertFalse(s.shouldLoad)
        assertTrue(failed.retry(online = true).shouldLoad)
    }

    @Test
    fun `앱이 뜬 뒤의 전체 새로고침은 흰 로딩 덮개로 가린다`() {
        val s = WebLoadState().started().finished(isAppPage = true).started()
        assertEquals(WebCover.LOADING, s.cover)
    }

    @Test
    fun `자동 재시도는 오류나 오프라인 상태에서만 한다`() {
        assertFalse(WebLoadState().started().finished(isAppPage = true).canAutoRetry)
        assertTrue(WebLoadState().started().mainFrameError().finished(isAppPage = false).canAutoRetry)
        assertTrue(WebLoadState().started().mainFrameError().finished(isAppPage = false).retry(online = false).canAutoRetry)
        assertFalse(WebLoadState().started().mainFrameError().finished(isAppPage = false).retry(online = true).started().canAutoRetry)
    }

    @Test
    fun `재시도 중 오류 콜백이 오면 로드 완료를 기다리지 않고 오류로 돌아간다`() {
        // 실패한 같은 주소를 다시 불러오면 WebView 는 오류 콜백만 주고 onPageFinished 를 주지 않는다.
        val s =
            WebLoadState().started().mainFrameError().finished(isAppPage = false)
                .retry(online = true).started().mainFrameError()
        assertEquals(WebCover.ERROR, s.cover)
    }

    @Test
    fun `아무 콜백도 오지 않으면 시간 초과로 오류가 된다`() {
        val retrying = WebLoadState().started().mainFrameError().finished(isAppPage = false).retry(online = true).started()
        assertEquals(WebCover.ERROR, retrying.timedOut().cover)
        // 첫 로드가 끝없이 늘어져도 같다
        assertEquals(WebCover.ERROR, WebLoadState().started().timedOut().cover)
    }

    @Test
    fun `시간 초과는 로드 중일 때만 의미가 있다`() {
        val done = WebLoadState().started().finished(isAppPage = true)
        assertEquals(done, done.timedOut())
    }
}
