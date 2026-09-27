package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.PushCampaign
import com.example.commerce.application.domain.entity.PushCampaignStatus
import com.example.commerce.application.domain.entity.PushConsent
import com.example.commerce.application.domain.entity.PushInboxItem
import com.example.commerce.application.domain.entity.PushTargetType
import java.time.LocalDateTime

/** 수신 동의 상태. 시각은 UTC(존 없음). 한 번도 바꾸지 않았으면 null. */
data class PushConsentResult(
    val marketing: Boolean,
    val marketingUpdatedAt: LocalDateTime?,
    val night: Boolean,
    val nightUpdatedAt: LocalDateTime?,
) {
    companion object {
        val DEFAULT = PushConsentResult(false, null, false, null)

        fun from(c: PushConsent) = PushConsentResult(c.marketing, c.marketingUpdatedAt, c.night, c.nightUpdatedAt)
    }
}

/** 백오피스 캠페인. 시각은 UTC(존 없음). */
data class AdminPushCampaignResult(
    val id: Long,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val targetType: PushTargetType,
    val targetId: Long?,
    val targetLabel: String?,
    val path: String,
    val status: PushCampaignStatus,
    val scheduledAt: LocalDateTime,
    val sentAt: LocalDateTime?,
    val canceledAt: LocalDateTime?,
    val nightApplied: Boolean,
    val targetUsers: Int,
    val targetDevices: Int,
    val successCount: Int,
    val failureCount: Int,
    val removedTokens: Int,
    val openedCount: Int,
    val createdBy: String,
    val createdAt: LocalDateTime,
    val failureMessage: String?,
) {
    companion object {
        fun from(c: PushCampaign) =
            AdminPushCampaignResult(
                id = requireNotNull(c.id),
                title = c.title,
                body = c.body,
                imageUrl = c.imageUrl,
                targetType = c.targetType,
                targetId = c.targetId,
                targetLabel = c.targetLabel,
                path = c.path(),
                status = c.status,
                scheduledAt = c.scheduledAt,
                sentAt = c.sentAt,
                canceledAt = c.canceledAt,
                nightApplied = c.nightApplied,
                targetUsers = c.targetUsers,
                targetDevices = c.targetDevices,
                successCount = c.successCount,
                failureCount = c.failureCount,
                removedTokens = c.removedTokens,
                openedCount = c.openedCount,
                createdBy = c.createdBy,
                createdAt = c.createdAt,
                failureMessage = c.failureMessage,
            )
    }
}

/** 그 시각에 보내면 받을 사람. [users]·[devices] 는 야간 규칙까지 적용한 실제 수신 대상. */
data class PushAudienceResult(
    val night: Boolean,
    val users: Long,
    val devices: Long,
    val consentedUsers: Long,
    val nightUsers: Long,
)

data class PushTestResult(
    val users: Int,
    val devices: Int,
    val success: Int,
    val failure: Int,
    val noDeviceUserIds: List<String>,
)

/** 앱 알림함 한 줄. 제목·내용은 광고 문구 없이 원문, [receivedAt] 은 UTC(존 없음). [id] 는 알림함 줄 id. */
data class NotificationItemResult(
    val id: Long,
    val campaignId: Long,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val path: String,
    val receivedAt: LocalDateTime,
    val read: Boolean,
) {
    companion object {
        fun of(
            item: PushInboxItem,
            campaign: PushCampaign,
        ) = NotificationItemResult(
            id = requireNotNull(item.id),
            campaignId = item.campaignId,
            title = campaign.title,
            body = campaign.body,
            imageUrl = campaign.imageUrl,
            path = campaign.path(),
            receivedAt = item.createdAt,
            read = item.readAt != null,
        )
    }
}
