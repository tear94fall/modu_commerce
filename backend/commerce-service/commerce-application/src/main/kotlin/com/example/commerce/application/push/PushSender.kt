package com.example.commerce.application.push

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 앱 푸시를 실제로 보내는 포트. 구현(commerce-api)은 Firebase Admin SDK(FirebasePushSender)이고,
 * 서비스 계정 키가 없으면 로그만 남기는 LoggingPushSender 다.
 */
interface PushSender {
    /**
     * [tokens] 에 같은 [message] 를 보낸다. 더 이상 쓸 수 없는 토큰(앱 삭제 등)은 [PushSendResult.invalidTokens] 로,
     * 전달된 토큰은 [PushSendResult.successTokens] 로 알려 준다(알림함은 전달된 사람에게만 남긴다).
     * 보내기 자체가 안 되면(인증 실패, 네트워크) 예외.
     */
    fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult
}

/** 알림 한 건. [data] 는 앱이 누를 때 읽는 값(type, campaignId, path). */
data class PushMessage(
    val title: String,
    val body: String,
    val imageUrl: String?,
    val data: Map<String, String>,
)

data class PushSendResult(
    val success: Int,
    val failure: Int,
    val invalidTokens: List<String> = emptyList(),
    val successTokens: List<String> = emptyList(),
)

/** 광고성 정보 전송 규칙(정보통신망법). 문구와 야간 시간은 서버가 정한다. */
object PushRules {
    const val TITLE_PREFIX = "(광고) "
    const val BODY_SUFFIX = "\n수신거부: 마이페이지 > 알림 설정"
    const val TEST_PREFIX = "[테스트] "
    const val CHUNK_SIZE = 500

    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /** 21:00~08:00(KST)는 야간 동의가 있어야 보낸다. */
    fun isNight(at: Instant): Boolean {
        val hour = at.atZone(KST).hour
        return hour >= 21 || hour < 8
    }

    fun title(title: String): String = TITLE_PREFIX + title

    fun body(body: String): String = body + BODY_SUFFIX

    fun utc(at: Instant): LocalDateTime = LocalDateTime.ofInstant(at, ZoneOffset.UTC)
}
