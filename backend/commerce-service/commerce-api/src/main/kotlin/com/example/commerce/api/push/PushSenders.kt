package com.example.commerce.api.push

import com.example.commerce.application.common.logger
import com.example.commerce.application.push.PushMessage
import com.example.commerce.application.push.PushRules
import com.example.commerce.application.push.PushSendResult
import com.example.commerce.application.push.PushSender
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification

/** Firebase Admin SDK 로 보낸다. 한 번에 500개씩(sendEachForMulticast 한도). */
class FirebasePushSender(
    private val messaging: FirebaseMessaging,
) : PushSender {
    override fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult {
        var success = 0
        var failure = 0
        val invalid = mutableListOf<String>()
        val delivered = mutableListOf<String>()
        tokens.chunked(PushRules.CHUNK_SIZE).forEach { chunk ->
            val response = messaging.sendEachForMulticast(build(chunk, message))
            success += response.successCount
            failure += response.failureCount
            // 응답은 보낸 토큰 순서와 같다.
            response.responses.forEachIndexed { i, r ->
                if (r.isSuccessful) {
                    delivered += chunk[i]
                } else if (r.exception?.messagingErrorCode in INVALID_TOKEN_CODES) {
                    invalid += chunk[i]
                }
            }
        }
        return PushSendResult(success, failure, invalid, delivered)
    }

    private fun build(
        tokens: List<String>,
        message: PushMessage,
    ): MulticastMessage {
        val notification =
            Notification
                .builder()
                .setTitle(message.title)
                .setBody(message.body)
                .apply { message.imageUrl?.let { setImage(it) } }
                .build()
        val android =
            AndroidNotification
                .builder()
                .setChannelId(CHANNEL_ID)
                .apply { message.imageUrl?.let { setImage(it) } }
                .build()
        return MulticastMessage
            .builder()
            .addAllTokens(tokens)
            .setNotification(notification)
            .setAndroidConfig(
                AndroidConfig
                    .builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(android)
                    .build(),
            ).putAllData(message.data)
            .build()
    }

    companion object {
        /** 앱의 알림 채널 "혜택·이벤트 알림". */
        const val CHANNEL_ID = "promotions"

        /** 이 토큰은 다시 써도 소용없다(앱 삭제·다른 프로젝트 토큰·형식 오류). 기기 목록에서 지운다. */
        val INVALID_TOKEN_CODES =
            setOf(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT, MessagingErrorCode.SENDER_ID_MISMATCH)
    }
}

/** 서비스 계정 키가 없을 때(IDE·키 없는 개발 서버). 보내지 않고 모두 성공으로 센다. */
class LoggingPushSender : PushSender {
    override fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult {
        logger.info { "[push:log-only] ${tokens.size} device(s): ${message.title} / ${message.data}" }
        return PushSendResult(tokens.size, 0, successTokens = tokens)
    }
}
