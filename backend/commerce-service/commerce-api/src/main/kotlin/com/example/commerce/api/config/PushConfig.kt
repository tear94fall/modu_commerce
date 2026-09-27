package com.example.commerce.api.config

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.io.File

/**
 * 푸시 캠페인 설정.
 * [firebaseCredentials] 는 Firebase 서비스 계정 키 파일 경로(compose 가 backend/secrets 를 /secrets 로 읽기 전용 마운트).
 * 비어 있거나 파일이 없으면 실제로 보내지 않고 로그만 남긴다(LoggingPushSender).
 */
@ConfigurationProperties("modu.push")
data class ModuPushProperties(
    val firebaseCredentials: String = "",
    /** 1분마다 예약 캠페인을 보내는 스케줄러. 테스트는 끄고 PushCampaignSendService.runDue 를 직접 부른다. */
    val schedulerEnabled: Boolean = true,
)

@Configuration
class PushConfig {
    @Bean
    fun pushSender(props: ModuPushProperties): PushSender {
        val path = props.firebaseCredentials.trim()
        if (path.isEmpty()) {
            logger.info { "modu.push.firebase-credentials is blank, push campaigns are logged only" }
            return LoggingPushSender()
        }
        val file = File(path)
        if (!file.isFile) {
            logger.warn { "firebase credentials file not found at $path, push campaigns are logged only" }
            return LoggingPushSender()
        }
        val credentials = file.inputStream().use { GoogleCredentials.fromStream(it) }
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
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun sendDueCampaigns() {
        try {
            val sent = pushCampaignSendService.runDue()
            if (sent > 0) logger.info { "push scheduler sent $sent campaign(s)" }
        } catch (e: Exception) {
            logger.error(e) { "push scheduler failed" }
        }
    }
}
