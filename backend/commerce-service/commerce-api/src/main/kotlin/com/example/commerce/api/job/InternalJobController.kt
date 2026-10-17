package com.example.commerce.api.job

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내부: batch-service(모두 시스템 "배치")가 부른다. 토큰 검사는 InternalApiFilter(X-Internal-Token).
 * 잡 목록은 콘솔이 등록할 잡을 고르는 데, 실행은 스케줄·수동 회차에 쓴다.
 */
@Tag(name = "배치 잡 (내부)", description = "서비스끼리만 호출(X-Internal-Token). batch-service 가 잡 목록을 읽고 한 회차를 돌린다.")
@RestController
@RequestMapping("/api-internal/v1/jobs")
class InternalJobController(
    private val jobRunner: JobRunner,
) {
    data class JobSummary(
        val name: String,
        val description: String,
    )

    data class Message(
        val message: String,
    )

    @Operation(summary = "잡 목록", description = "이 서비스가 돌릴 수 있는 잡의 이름과 설명. 이름순.")
    @GetMapping
    fun list(): List<JobSummary> = jobRunner.all().map { JobSummary(it.name, it.description) }

    @Operation(
        summary = "잡 한 회차 실행",
        description =
            "끝날 때까지 기다렸다가 결과를 돌려준다. 성공 200·실패 500 모두 같은 모양(job·outcome·durationMs·requestId·result|error). " +
                "requestId(job-…)로 OpenSearch 에서 그 회차의 로그를 모두 본다. 모르는 잡 404, 이미 돌고 있으면 409.",
    )
    @PostMapping("/{name}/run")
    fun run(
        @Parameter(description = "잡 이름", example = "point-outbox-relay")
        @PathVariable name: String,
    ): ResponseEntity<JobRun> {
        val run = jobRunner.run(name)
        val status = if (run.outcome == "ok") HttpStatus.OK else HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status).body(run)
    }

    @ExceptionHandler(UnknownJobException::class)
    fun unknownJob(): ResponseEntity<Message> = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Message("unknown job"))

    @ExceptionHandler(JobAlreadyRunningException::class)
    fun alreadyRunning(): ResponseEntity<Message> = ResponseEntity.status(HttpStatus.CONFLICT).body(Message("job already running"))
}
