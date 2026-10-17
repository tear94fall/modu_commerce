package com.example.commerce.api.job

/**
 * 스케줄러나 batch-service(모두 시스템 "배치")가 한 회차를 돌릴 수 있는 작업.
 * [run] 은 결과 요약(처리 건수 등)을 돌려주고, 실패는 예외로 알린다. 두 번 돌아도 안전해야 한다(멱등).
 * 이름은 `job.run` 이벤트의 `job` 필드이자 `/api-internal/v1/jobs/{name}/run` 의 경로다.
 */
interface Job {
    val name: String
    val description: String

    fun run(): Map<String, Any?>
}
