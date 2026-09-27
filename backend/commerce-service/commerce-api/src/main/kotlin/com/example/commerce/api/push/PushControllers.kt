package com.example.commerce.api.push

import com.example.commerce.api.common.CustomerRequired
import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.application.domain.entity.PushCampaignStatus
import com.example.commerce.application.domain.entity.PushPlatform
import com.example.commerce.application.domain.entity.PushTargetType
import com.example.commerce.application.usecase.command.PushCampaignCommand
import com.example.commerce.application.usecase.command.PushContentCommand
import com.example.commerce.application.usecase.push.AdminPushCampaignUseCase
import com.example.commerce.application.usecase.push.MarkPushOpenedUseCase
import com.example.commerce.application.usecase.push.PushConsentUseCase
import com.example.commerce.application.usecase.push.PushDeviceUseCase
import com.example.commerce.application.usecase.push.PushInboxUseCase
import com.example.commerce.application.usecase.result.AdminPushCampaignResult
import com.example.commerce.application.usecase.result.NotificationItemResult
import com.example.commerce.application.usecase.result.PushAudienceResult
import com.example.commerce.application.usecase.result.PushConsentResult
import com.example.commerce.application.usecase.result.PushTestResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** 앱: 푸시 토큰, 광고성 정보 수신 동의, 알림 열어 봄. */
@RestController
@RequestMapping("/api/v1")
class PushController(
    private val pushDeviceUseCase: PushDeviceUseCase,
    private val pushConsentUseCase: PushConsentUseCase,
    private val markPushOpenedUseCase: MarkPushOpenedUseCase,
) {
    @CustomerRequired
    @PutMapping("/me/push/devices")
    fun registerDevice(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PushDeviceRequest,
    ): ResponseEntity<Void> {
        val token = requireNotNull(request.token?.takeIf { it.isNotBlank() }) { "푸시 토큰이 없습니다." }
        pushDeviceUseCase.register(jwt.userId(), token, request.platform ?: PushPlatform.ANDROID)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me/push/devices")
    fun removeDevice(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam token: String,
    ): ResponseEntity<Void> {
        pushDeviceUseCase.remove(jwt.userId(), token)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me/push/consent")
    fun consent(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PushConsentResult> = ResponseEntity.ok(pushConsentUseCase.get(jwt.userId()))

    @CustomerRequired
    @PutMapping("/me/push/consent")
    fun changeConsent(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PushConsentRequest,
    ): ResponseEntity<PushConsentResult> {
        val marketing = requireNotNull(request.marketing) { "혜택·이벤트 알림 동의 여부가 없습니다." }
        val night = requireNotNull(request.night) { "야간 알림 동의 여부가 없습니다." }
        return ResponseEntity.ok(pushConsentUseCase.change(jwt.userId(), marketing, night))
    }

    @PostMapping("/push/campaigns/{id}/opened")
    fun opened(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        markPushOpenedUseCase.execute(id, jwt.userId())
        return ResponseEntity.noContent().build()
    }
}

/** 앱: 알림함. 받은 지 30일 안쪽의 캠페인 알림, 최신 순. */
@CustomerRequired
@RestController
@RequestMapping("/api/v1/me/notifications")
class NotificationController(
    private val pushInboxUseCase: PushInboxUseCase,
) {
    @GetMapping
    fun notifications(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<NotificationItemResult>> =
        ResponseEntity.ok(PageResponse.from(pushInboxUseCase.page(jwt.userId(), page, size)) { it })

    @GetMapping("/unread-count")
    fun unreadCount(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Map<String, Long>> = ResponseEntity.ok(mapOf("unread" to pushInboxUseCase.unread(jwt.userId())))

    @PostMapping("/{id}/read")
    fun read(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        pushInboxUseCase.markRead(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/read-all")
    fun readAll(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        pushInboxUseCase.markAllRead(jwt.userId())
        return ResponseEntity.noContent().build()
    }
}

/** 백오피스: 푸시 캠페인(광고 문구·야간 규칙·동의 확인은 서버가 한다). */
@RestController
@RequestMapping("/api-admin/v1/push-campaigns")
class AdminPushCampaignController(
    private val adminPushCampaignUseCase: AdminPushCampaignUseCase,
) {
    @GetMapping
    fun campaigns(
        @RequestParam(required = false) status: PushCampaignStatus?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<AdminPushCampaignResult>> =
        ResponseEntity.ok(PageResponse.from(adminPushCampaignUseCase.search(status, q, page, size)) { it })

    @GetMapping("/audience")
    fun audience(
        @RequestParam(required = false) at: String?,
    ): ResponseEntity<PushAudienceResult> =
        ResponseEntity.ok(adminPushCampaignUseCase.audience(at?.takeIf { it.isNotBlank() }?.let { parseOffset(it).toInstant() }))

    @GetMapping("/{id}")
    fun campaign(
        @PathVariable id: Long,
    ): ResponseEntity<AdminPushCampaignResult> = ResponseEntity.ok(adminPushCampaignUseCase.get(id))

    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PushCampaignRequest,
    ): ResponseEntity<AdminPushCampaignResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            adminPushCampaignUseCase.create(PushCampaignCommand(request.toContent(), request.scheduledAt, jwt.subject)),
        )

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<AdminPushCampaignResult> = ResponseEntity.ok(adminPushCampaignUseCase.cancel(id))

    @PostMapping("/test")
    fun test(
        @RequestBody request: PushTestRequest,
    ): ResponseEntity<PushTestResult> =
        ResponseEntity.ok(
            adminPushCampaignUseCase.test(
                toContent(request.title, request.body, request.imageUrl, request.targetType, request.targetId),
                request.userIds.orEmpty(),
            ),
        )

    companion object {
        /** 쿼리 문자열의 "+09:00" 은 인코딩하지 않으면 공백으로 온다. 되돌려서 읽는다. */
        fun parseOffset(value: String): OffsetDateTime =
            try {
                OffsetDateTime.parse(value.trim().replace(' ', '+'))
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("시각은 2026-09-27T21:30:00+09:00 형식으로 보내세요.")
            }
    }
}

data class PushDeviceRequest(
    val token: String? = null,
    val platform: PushPlatform? = null,
)

data class PushConsentRequest(
    val marketing: Boolean? = null,
    val night: Boolean? = null,
)

/** 캠페인 만들기. [scheduledAt] 은 오프셋이 있는 ISO-8601("2026-09-27T21:30:00+09:00"), null 이면 지금 보낸다. */
data class PushCampaignRequest(
    val title: String? = null,
    val body: String? = null,
    val imageUrl: String? = null,
    val targetType: PushTargetType? = null,
    val targetId: Long? = null,
    val scheduledAt: OffsetDateTime? = null,
) {
    fun toContent(): PushContentCommand = toContent(title, body, imageUrl, targetType, targetId)
}

data class PushTestRequest(
    val title: String? = null,
    val body: String? = null,
    val imageUrl: String? = null,
    val targetType: PushTargetType? = null,
    val targetId: Long? = null,
    val userIds: List<String>? = null,
)

private fun toContent(
    title: String?,
    body: String?,
    imageUrl: String?,
    targetType: PushTargetType?,
    targetId: Long?,
) = PushContentCommand(
    title = title?.trim().orEmpty(),
    body = body?.trim().orEmpty(),
    imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
    targetType = requireNotNull(targetType) { "보낼 대상 종류를 고르세요." },
    targetId = targetId,
)
