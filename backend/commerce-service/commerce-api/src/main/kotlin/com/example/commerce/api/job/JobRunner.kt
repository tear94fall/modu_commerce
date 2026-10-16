package com.example.commerce.api.job

import com.example.commerce.api.logging.JobMdc
import com.example.commerce.api.logging.RequestContextFilter
import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** 한 회차의 결과. batch-service 가 이력(`batch_job_run`)에 그대로 적는다. 비어 있는 result·error 는 JSON 에서 뺀다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JobRun(
    val job: String,
    /** `ok` 또는 `failed`. */
    val outcome: String,
    val durationMs: Long,
    /** `job-` 접두. OpenSearch 에서 이 회차의 로그를 모두 보는 열쇠(`attributes.requestId`). */
    val requestId: String,
    val result: Map<String, Any?>? = null,
    val error: String? = null,
)

class UnknownJobException(val jobName: String) : RuntimeException("unknown job")

class JobAlreadyRunningException(val jobName: String) : RuntimeException("job already running")

/**
 * 이름으로 [Job] 을 찾아 한 회차를 돌린다. 스케줄러(`@Scheduled`)와 `/api-internal/v1/jobs/{name}/run` 이 같은 길을 쓴다.
 * - [JobMdc.run] 안에서 돌려 `job.run` 이벤트 한 줄과 `job-` requestId 가 그대로 남는다.
 * - 잡당 동시 실행은 하나다. 돌고 있는 잡을 다시 부르면 [JobAlreadyRunningException].
 * - 잡의 예외는 여기서 잡아 `outcome=failed` 로 돌려준다(ERROR 로그와 스택은 JobMdc 가 이미 남겼다). 스케줄러는 그래서 try/catch 가 필요 없다.
 */
@Component
class JobRunner(jobs: List<Job>) {
    private val byName: Map<String, Job> = jobs.associateBy { it.name }
    private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        require(byName.size == jobs.size) { "잡 이름이 겹친다: ${jobs.map { it.name }}" }
    }

    fun all(): List<Job> = byName.values.sortedBy { it.name }

    fun run(name: String): JobRun {
        val job = byName[name] ?: throw UnknownJobException(name)
        if (!running.add(name)) throw JobAlreadyRunningException(name)
        val started = System.nanoTime()
        var requestId = ""
        try {
            return try {
                val result =
                    JobMdc.run(name) {
                        requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID)
                        job.run()
                    }
                JobRun(name, "ok", elapsedMs(started), requestId, result = result)
            } catch (e: Exception) {
                JobRun(name, "failed", elapsedMs(started), requestId, error = e.toString())
            }
        } finally {
            running.remove(name)
        }
    }

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000
}
