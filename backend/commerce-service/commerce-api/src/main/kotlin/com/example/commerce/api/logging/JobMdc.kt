package com.example.commerce.api.logging

import org.slf4j.MDC

/**
 * 스케줄 작업(등급 산정, 적립 재시도, 푸시 캠페인)의 로그에 요청 없이도 묶음 키를 남긴다.
 * `requestId = "job-" + 새 값`, `job = 작업 이름`. 작업이 끝나면 지운다.
 */
object JobMdc {
    const val MDC_JOB = "job"
    const val REQUEST_ID_PREFIX = "job-"

    fun <T> run(
        name: String,
        block: () -> T,
    ): T {
        MDC.put(RequestContextFilter.MDC_REQUEST_ID, REQUEST_ID_PREFIX + RequestContextFilter.newRequestId())
        MDC.put(MDC_JOB, name)
        try {
            return block()
        } finally {
            MDC.remove(MDC_JOB)
            MDC.remove(RequestContextFilter.MDC_REQUEST_ID)
        }
    }
}
