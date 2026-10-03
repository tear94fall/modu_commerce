package com.example.commerce.api.config

import com.example.commerce.api.logging.JobMdc
import com.example.commerce.api.push.FirebasePushSender
import com.example.commerce.api.push.LoggingPushSender
import com.example.commerce.application.common.logger
import com.example.commerce.application.push.PushSender
import com.example.commerce.application.service.PushCampaignSendService
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64

/**
 * 푸시 캠페인 설정.
 * [firebaseCredentialsBase64] 는 Firebase 서비스 계정 키(JSON)를 base64 로 감싼 값 — config-repo(commerce-service.yml)가 {cipher} 로 내려준다.
 * [firebaseCredentials] 는 키 파일 경로(설정 서버 없이 로컬에서 띄울 때의 대안). base64 가 있으면 그것을 쓴다.
 * 둘 다 비어 있거나 파일이 없으면 실제로 보내지 않고 로그만 남긴다(LoggingPushSender).
 */
@ConfigurationProperties("modu.push")
data class ModuPushProperties(
    val firebaseCredentialsBase64: String = "",
    val firebaseCredentials: String = "",
    /** 1분마다 예약 캠페인을 보내는 스케줄러. 테스트는 끄고 PushCampaignSendService.runDue 를 직접 부른다. */
    val schedulerEnabled: Boolean = true,
)

@Configuration
class PushConfig {
    @Bean
    fun pushSender(props: ModuPushProperties): PushSender {
        val credentials = loadCredentials(props) ?: return LoggingPushSender()
        val projectId = (credentials as? ServiceAccountCredentials)?.projectId
        val app =
            FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }
                ?: FirebaseApp.initializeApp(
                    FirebaseOptions
                        .builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build(),
                    APP_NAME,
                )
        logger.info { "push campaigns are sent with Firebase (project $projectId)" }
        return FirebasePushSender(FirebaseMessaging.getInstance(app))
    }

    /** base64 설정 → 파일 경로 순서로 자격 증명을 찾는다. 없으면 null(로그만 남기는 발송기). 내용은 절대 로그에 남기지 않는다. */
    private fun loadCredentials(props: ModuPushProperties): GoogleCredentials? {
        val base64 = props.firebaseCredentialsBase64.trim()
        if (base64.isNotEmpty()) {
            val bytes =
                runCatching { Base64.getDecoder().decode(base64) }.getOrElse {
                    logger.warn { "modu.push.firebase-credentials-base64 is not valid base64, push campaigns are logged only" }
                    return null
                }
            logger.info { "firebase credentials loaded from config (base64)" }
            return ByteArrayInputStream(bytes).use { GoogleCredentials.fromStream(it) }
        }
        val path = props.firebaseCredentials.trim()
        if (path.isEmpty()) {
            logger.info { "no firebase credentials configured, push campaigns are logged only" }
            return null
        }
        val file = File(path)
        if (!file.isFile) {
            logger.warn { "firebase credentials file not found at $path, push campaigns are logged only" }
            return null
        }
        logger.info { "firebase credentials loaded from file" }
        return file.inputStream().use { GoogleCredentials.fromStream(it) }
    }

    companion object {
        /** 기본 앱 이름을 쓰지 않는다(다른 라이브러리가 기본 FirebaseApp 을 만들어도 부딪치지 않게). */
        const val APP_NAME = "commerce"
    }
}

/** 예약 캠페인 스케줄러. `modu.push.scheduler-enabled=false` 면 스케줄링 자체를 켜지 않는다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "modu.push", name = ["scheduler-enabled"], havingValue = "true", matchIfMissing = true)
class PushSchedulingConfig(
    private val pushCampaignSendService: PushCampaignSendService,
) {
    // 파드가 여러 개여도 같은 캠페인을 두 번 보내지 않게 한 곳에서만 돈다(SchedulerLockConfig). 한 번의 발송은 최대 5분.
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @SchedulerLock(name = "commerce:push-campaigns", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    fun sendDueCampaigns() {
        JobMdc.run("push-campaigns") {
            try {
                val sent = pushCampaignSendService.runDue()
                if (sent > 0) logger.info { "push scheduler sent $sent campaign(s)" }
            } catch (e: Exception) {
                logger.error(e) { "push scheduler failed" }
            }
        }
    }
}
