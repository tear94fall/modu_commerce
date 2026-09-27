package com.example.commerce.application.push

import com.example.commerce.application.common.logger
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * "지금 보내기"를 요청 스레드 밖에서 돌린다(관리자 화면이 FCM 응답을 기다리지 않는다).
 * `modu.push.async-send=false`(테스트)면 부른 스레드에서 바로 돌린다.
 * 스프링 TaskExecutor 빈으로 두지 않는다 — 그러면 Boot 의 기본 applicationTaskExecutor 가 빠진다.
 */
@Component
class PushAsyncRunner(
    @Value("\${modu.push.async-send:true}") private val async: Boolean,
) : DisposableBean {
    private val executor: ExecutorService? =
        if (async) {
            Executors.newFixedThreadPool(2) { r -> Thread(r, "push-send").apply { isDaemon = true } }
        } else {
            null
        }

    fun run(task: () -> Unit) {
        val job =
            Runnable {
                try {
                    task()
                } catch (e: Exception) {
                    logger.error(e) { "push async task failed" }
                }
            }
        if (executor == null) job.run() else executor.execute(job)
    }

    override fun destroy() {
        executor?.shutdown()
    }
}
