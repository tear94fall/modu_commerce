package com.example.commerce.api.logging

import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * 배치 잡(등급 산정, 푸시 캠페인, 포인트 아웃박스 릴레이·대사)의 한 회차를 감싼다.
 * - 로그에 요청 없이도 묶음 키를 남긴다: `requestId = "job-" + 새 값`, `job = 작업 이름`. 작업이 끝나면 지운다.
 * - MDC 의 requestId 가 `job-` 로 시작하면(batch-service 가 `/api-internal/v1/jobs/{name}/run` 으로 돌리는 회차 — RequestContextFilter 가 X-Request-Id 의 회차 id 를
 *   넣어 둔다) 새로 만들지 않고 그대로 쓴다. 그래야 batch-service 의 회차 id 하나로 양쪽 로그가 묶인다. 그 id 는 요청이 끝날 때 필터가 지운다.
 * - 그 밖의 requestId(헤더 없이 curl 로 부른 요청의 접두 없는 id)는 회차 id 가 아니다 — `job-` id 를 새로 만들어 쓰고, 끝나면 원래 id 를 되돌려 놓는다(그 뒤의 api.access·http.access 가 쓴다).
 * - 끝나면 `job.run` 이벤트 한 줄: 작업 이름·결과(ok/failed)·소요 시간, 실패면 error 와 스택(ERROR). http.access·api.access·kafka.publish 와
 *   같은 방식으로 OpenSearch 에서 `attributes.event:"job.run"` 으로 배치 실행 이력을 본다. 예외는 그대로 던진다.
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
        val previousRequestId: String? = MDC.get(RequestContextFilter.MDC_REQUEST_ID)
        val ownRequestId = previousRequestId?.startsWith(REQUEST_ID_PREFIX) != true
        if (ownRequestId) MDC.put(RequestContextFilter.MDC_REQUEST_ID, REQUEST_ID_PREFIX + RequestContextFilter.newRequestId())
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
            if (ownRequestId) {
                if (previousRequestId ==
                    null
                ) {
                    MDC.remove(RequestContextFilter.MDC_REQUEST_ID)
                } else {
                    MDC.put(RequestContextFilter.MDC_REQUEST_ID, previousRequestId)
                }
            }
        }
    }

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000
}
