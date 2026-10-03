package com.example.commerce.api.config

import com.example.commerce.api.push.LoggingPushSender
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Firebase 자격 증명 선택: base64 설정 → 파일 → 없으면 로그만 남기는 발송기. 잘못된 값은 기동을 막지 않는다. */
class PushConfigTest {
    private val config = PushConfig()

    @Test
    fun `no credentials at all falls back to logging sender`() {
        assertTrue(config.pushSender(ModuPushProperties()) is LoggingPushSender)
    }

    @Test
    fun `invalid base64 falls back to logging sender instead of failing startup`() {
        assertTrue(config.pushSender(ModuPushProperties(firebaseCredentialsBase64 = "@@not-base64@@")) is LoggingPushSender)
    }

    @Test
    fun `missing file falls back to logging sender`() {
        assertTrue(config.pushSender(ModuPushProperties(firebaseCredentials = "/nope/firebase.json")) is LoggingPushSender)
    }
}
