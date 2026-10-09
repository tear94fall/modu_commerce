package com.example.commerce.application.point

/** 포인트 대사에서 찾은 이상을 지표로 남긴다(commerce-api 가 Micrometer 카운터로 구현). [type]: spend_missing, refund_missing, amount_mismatch. */
fun interface PointAnomalyRecorder {
    fun record(type: String)
}
