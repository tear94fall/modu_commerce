package com.example.commerce.application.usecase.push

import com.example.commerce.application.domain.entity.PushCampaignStatus
import com.example.commerce.application.domain.entity.PushPlatform
import com.example.commerce.application.push.PushAsyncRunner
import com.example.commerce.application.service.PushCampaignCommandService
import com.example.commerce.application.service.PushCampaignQueryService
import com.example.commerce.application.service.PushCampaignSendService
import com.example.commerce.application.service.PushConsentCommandService
import com.example.commerce.application.service.PushConsentQueryService
import com.example.commerce.application.service.PushDeviceService
import com.example.commerce.application.service.PushInboxCommandService
import com.example.commerce.application.service.PushInboxQueryService
import com.example.commerce.application.usecase.command.PushCampaignCommand
import com.example.commerce.application.usecase.command.PushContentCommand
import com.example.commerce.application.usecase.result.AdminPushCampaignResult
import com.example.commerce.application.usecase.result.NotificationItemResult
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.PushAudienceResult
import com.example.commerce.application.usecase.result.PushConsentResult
import com.example.commerce.application.usecase.result.PushTestResult
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 앱: 푸시 토큰 등록·삭제. */
@Component
class PushDeviceUseCase(
    private val pushDeviceService: PushDeviceService,
) {
    fun register(
        userId: String,
        token: String,
        platform: PushPlatform,
    ) {
        try {
            pushDeviceService.register(userId, token.trim(), platform)
        } catch (e: DataIntegrityViolationException) {
            // 같은 토큰이 동시에 두 번 등록됐다. 이제는 행이 있으니 한 번 더 하면 옮기기(고치기)가 된다.
            pushDeviceService.register(userId, token.trim(), platform)
        }
    }

    fun remove(
        userId: String,
        token: String,
    ) = pushDeviceService.remove(userId, token.trim())
}

/** 앱: 광고성 정보 수신 동의. */
@Component
class PushConsentUseCase(
    private val pushConsentQueryService: PushConsentQueryService,
    private val pushConsentCommandService: PushConsentCommandService,
) {
    fun get(userId: String): PushConsentResult = pushConsentQueryService.get(userId)

    fun change(
        userId: String,
        marketing: Boolean,
        night: Boolean,
    ): PushConsentResult = pushConsentCommandService.change(userId, marketing, night)
}

/** 앱: 알림을 눌러 열었다(사람마다 한 번 센다). 알림함의 그 줄도 읽음으로 바꾼다. */
@Component
class MarkPushOpenedUseCase(
    private val pushCampaignCommandService: PushCampaignCommandService,
    private val pushInboxCommandService: PushInboxCommandService,
) {
    fun execute(
        campaignId: Long,
        userId: String,
    ) {
        pushInboxCommandService.markCampaignRead(userId, campaignId)
        try {
            pushCampaignCommandService.recordOpen(campaignId, userId)
        } catch (e: DataIntegrityViolationException) {
            // 같은 사람이 동시에 두 번 열었다. 하나만 센다.
        }
    }
}

/** 앱: 알림함(받은 캠페인 30일치). */
@Component
class PushInboxUseCase(
    private val pushInboxQueryService: PushInboxQueryService,
    private val pushInboxCommandService: PushInboxCommandService,
) {
    fun page(
        userId: String,
        page: Int,
        size: Int,
    ): PageResult<NotificationItemResult> = PageResult.from(pushInboxQueryService.page(userId, page, size)) { it }

    fun unread(userId: String): Long = pushInboxQueryService.unread(userId)

    fun markRead(
        userId: String,
        id: Long,
    ) = pushInboxCommandService.markRead(userId, id)

    fun markAllRead(userId: String) = pushInboxCommandService.markAllRead(userId)
}

/** 백오피스: 푸시 캠페인. */
@Component
class AdminPushCampaignUseCase(
    private val pushCampaignQueryService: PushCampaignQueryService,
    private val pushCampaignCommandService: PushCampaignCommandService,
    private val pushCampaignSendService: PushCampaignSendService,
    private val pushAsyncRunner: PushAsyncRunner,
) {
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun search(
        status: PushCampaignStatus?,
        q: String?,
        page: Int,
        size: Int,
    ): PageResult<AdminPushCampaignResult> =
        PageResult.from(pushCampaignQueryService.adminPage(status, q, page, size), AdminPushCampaignResult::from)

    fun get(id: Long): AdminPushCampaignResult = AdminPushCampaignResult.from(pushCampaignQueryService.find(id))

    /** 만든다. 지금 보내기(예약 시각 없음)면 커밋된 뒤 따로 보낸다(선점 규칙이 같아 스케줄러와 겹쳐도 한 번만 나간다). */
    fun create(command: PushCampaignCommand): AdminPushCampaignResult {
        val id = requireNotNull(pushCampaignCommandService.create(command).id)
        if (command.scheduledAt == null) {
            pushAsyncRunner.run { pushCampaignSendService.send(id) }
        }
        return get(id)
    }

    fun cancel(id: Long): AdminPushCampaignResult {
        pushCampaignCommandService.cancel(id)
        return get(id)
    }

    fun audience(at: Instant?): PushAudienceResult = pushCampaignQueryService.audience(at)

    fun test(
        content: PushContentCommand,
        userIds: List<String>,
    ): PushTestResult = pushCampaignSendService.test(content, userIds)
}
