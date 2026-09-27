package com.example.commerce.application.push

import org.springframework.stereotype.Component

/** 이 모듈의 스프링 테스트용. 실제 구현(Firebase)은 commerce-api 에 있다. */
@Component
class StubPushSender : PushSender {
    override fun send(
        tokens: List<String>,
        message: PushMessage,
    ) = PushSendResult(tokens.size, 0, successTokens = tokens)
}
