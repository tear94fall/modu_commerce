package com.example.commerce.api.logging

import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * 스케줄 작업(등급 산정, 적립 재시도, 푸시 캠페인, 포인트 아웃박스·대사)의 한 회차를 감싼다.
 * - 로그에 요청 없이도 묶음 키를 남긴다: `requestId = "job-" + 새 값`, `job = 작업 이름`. 작업이 끝나면 지운다.
 * - 끝나면 `job.run` 이벤트 한 줄: 작업 이름·결과(ok/failed)·소요 시간, 실패면 error 와 스택(ERROR). http.access·api.access·kafka.publish 와
 *   같은 방식으로 OpenSearch 에서 `attributes.event:"job.run"` 으로 배치 실행 이력을 본다. 예외는 그대로 던진다.
 * ShedLock 이 락을 못 잡아 본문이 돌지 않은 회차는 여기까지 오지 않으므로 이벤트도 없다.
 */
object JobMdc {
    const val MDC_JOB = "job"
    const val REQUEST_ID_PREFIX = "job-"
    const val EVENT = "job.run"
    const val LOGGER = "job.run"

    private val log = LoggerFactory.getLogger(LOGGER)

    fun <T> run(
        name: String,
        block: () -> T,
    ): T {
        MDC.put(RequestContextFilter.MDC_REQUEST_ID, REQUEST_ID_PREFIX + RequestContextFilter.newRequestId())
        MDC.put(MDC_JOB, name)
        val started = System.nanoTime()
        try {
            val result = block()
            val durationMs = elapsedMs(started)
            log.info(
                "job {} ok {}ms",
                name,
                durationMs,
                kv("event", EVENT),
                kv("job", name),
                kv("outcome", "ok"),
                kv("durationMs", durationMs),
            )
            return result
        } catch (e: Throwable) {
            val durationMs = elapsedMs(started)
            log.error(
                "job {} failed {}ms: {}",
                name,
                durationMs,
                e.message,
                kv("event", EVENT),
                kv("job", name),
                kv("outcome", "failed"),
                kv("durationMs", durationMs),
                kv("error", e.toString()),
                e,
            )
            throw e
        } finally {
            MDC.remove(MDC_JOB)
            MDC.remove(RequestContextFilter.MDC_REQUEST_ID)
        }
    }

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000
}
