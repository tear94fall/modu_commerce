package com.example.commerce.application.point

import org.springframework.stereotype.Component

/** 이 모듈의 스프링 테스트용. 실제 구현(Micrometer 카운터)은 commerce-api 에 있다. */
@Component
class StubPointAnomalyRecorder : PointAnomalyRecorder {
    val recorded = mutableListOf<String>()

    override fun record(type: String) {
        recorded += type
    }
}
