package com.example.moducommerce

/**
 * 액세스 토큰(TTL 1시간)이 만료돼 401 이 오면 refresh 토큰으로 한 번만 갱신하고 다시 부른다.
 * 갱신할 수 없으면 AuthException(401) 로 알린다 — 화면은 이걸 세션 만료로 보고 로그인으로 보낸다.
 * 토큰 저장소와 갱신 호출은 주입받아 JVM 테스트에서 가짜로 바꿀 수 있게 한다. 백그라운드 스레드에서 쓴다.
 */
class TokenRefresher(
    private val loadAccess: () -> String?,
    private val loadRefresh: () -> String?,
    private val renew: (String) -> TokenResponse,
    private val save: (TokenResponse) -> Unit,
) {

    fun <T> withFreshAccess(call: (String) -> T): T {
        val access = loadAccess() ?: throw sessionExpired("저장된 액세스 토큰이 없습니다")
        try {
            return call(access)
        } catch (e: ModuAuthClient.AuthException) {
            if (e.status != 401) throw e
        }
        val refreshToken = loadRefresh() ?: throw sessionExpired("refresh 토큰이 없습니다")
        val renewed = try {
            renew(refreshToken)
        } catch (e: Exception) {
            throw sessionExpired("토큰 갱신 실패: ${e.message}")
        }
        val renewedAccess = renewed.accessToken ?: throw sessionExpired("갱신 응답에 액세스 토큰이 없습니다")
        save(renewed)
        return call(renewedAccess)
    }

    companion object {
        fun isSessionExpired(e: Throwable): Boolean = e is ModuAuthClient.AuthException && e.status == 401

        private fun sessionExpired(reason: String) = ModuAuthClient.AuthException(401, reason)
    }
}
