package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

enum class PushPlatform { ANDROID }

/** 캠페인을 누르면 갈 화면. PRODUCT·PROMOTION 은 대상 id 가 있다. */
enum class PushTargetType { PRODUCT, PROMOTION, COUPONS, HOME }

enum class PushCampaignStatus { SCHEDULED, SENDING, SENT, CANCELED, FAILED }

/**
 * 앱 푸시 토큰. 토큰은 기기마다 하나라 유니크다. 다른 사람이 같은 기기에서 로그인해 다시 등록하면 그 사람에게 옮긴다.
 * 시각은 모두 UTC 다.
 */
@Entity
@Table(
    name = "push_devices",
    uniqueConstraints = [UniqueConstraint(name = "uk_push_device_token", columnNames = ["token"])],
    indexes = [Index(name = "ix_push_device_user", columnList = "user_id")],
)
class PushDevice(
    userId: String,
    @Column(name = "token", nullable = false, length = 512)
    val token: String,
    platform: PushPlatform,
    now: LocalDateTime,
) : IdentityEntity() {
    @Column(name = "user_id", nullable = false, length = 64)
    var userId: String = userId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    var platform: PushPlatform = platform
        protected set

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = now
        protected set

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: LocalDateTime = now
        protected set

    /** 다시 등록(앱 실행·로그인). 다른 사람의 토큰이었으면 이 사람에게 옮긴다. */
    fun seen(
        userId: String,
        platform: PushPlatform,
        now: LocalDateTime,
    ) {
        this.userId = userId
        this.platform = platform
        this.lastSeenAt = now
    }
}

/**
 * 광고성 정보 수신 동의(정보통신망법). 기본은 둘 다 거부이고, 야간(21~08시 KST) 동의는 혜택 알림 동의가 있을 때만 켤 수 있다.
 * 시각은 해당 값이 마지막으로 바뀐 때(UTC). 한 번도 바꾸지 않았으면 null 이다.
 */
@Entity
@Table(name = "push_consents")
class PushConsent(
    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
) {
    @Column(name = "marketing", nullable = false)
    var marketing: Boolean = false
        protected set

    @Column(name = "marketing_updated_at")
    var marketingUpdatedAt: LocalDateTime? = null
        protected set

    @Column(name = "night", nullable = false)
    var night: Boolean = false
        protected set

    @Column(name = "night_updated_at")
    var nightUpdatedAt: LocalDateTime? = null
        protected set

    /** 바뀐 값의 시각만 새로 적는다. 혜택 알림을 끄면 야간 알림도 꺼진다. */
    fun change(
        marketing: Boolean,
        night: Boolean,
        now: LocalDateTime,
    ) {
        val nightValue = marketing && night
        if (marketing != this.marketing) {
            this.marketing = marketing
            this.marketingUpdatedAt = now
        }
        if (nightValue != this.night) {
            this.night = nightValue
            this.nightUpdatedAt = now
        }
    }
}

/** 관리자가 보내는 광고 푸시 한 건. 시각은 모두 UTC 다. */
@Entity
@Table(name = "push_campaigns", indexes = [Index(name = "ix_push_campaign_due", columnList = "status,scheduled_at")])
class PushCampaign(
    @Column(name = "title", nullable = false, length = 40)
    val title: String,
    @Column(name = "body", nullable = false, length = 120)
    val body: String,
    @Column(name = "image_url", length = 500)
    val imageUrl: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    val targetType: PushTargetType,
    @Column(name = "target_id")
    val targetId: Long?,
    /** 대상 이름 스냅숏(상품명, 기획전 제목). 쿠폰함·홈은 null. */
    @Column(name = "target_label", length = 200)
    val targetLabel: String?,
    @Column(name = "scheduled_at", nullable = false)
    val scheduledAt: LocalDateTime,
    @Column(name = "created_by", nullable = false, length = 64)
    val createdBy: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
) : IdentityEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: PushCampaignStatus = PushCampaignStatus.SCHEDULED
        protected set

    @Column(name = "night_applied", nullable = false)
    var nightApplied: Boolean = false
        protected set

    @Column(name = "target_users", nullable = false)
    var targetUsers: Int = 0
        protected set

    @Column(name = "target_devices", nullable = false)
    var targetDevices: Int = 0
        protected set

    @Column(name = "success_count", nullable = false)
    var successCount: Int = 0
        protected set

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0
        protected set

    @Column(name = "removed_tokens", nullable = false)
    var removedTokens: Int = 0
        protected set

    @Column(name = "opened_count", nullable = false)
    var openedCount: Int = 0
        protected set

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null
        protected set

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null
        protected set

    @Column(name = "failure_message", length = 500)
    var failureMessage: String? = null
        protected set

    /** 보내기 시작(선점 뒤). 받는 사람 수와 야간 규칙 적용 여부를 남긴다. */
    fun started(
        nightApplied: Boolean,
        users: Int,
        devices: Int,
    ) {
        this.nightApplied = nightApplied
        this.targetUsers = users
        this.targetDevices = devices
    }

    fun progress(
        success: Int,
        failure: Int,
        removed: Int,
    ) {
        successCount = success
        failureCount = failure
        removedTokens = removed
    }

    fun sent(now: LocalDateTime) {
        status = PushCampaignStatus.SENT
        sentAt = now
    }

    fun failed(
        message: String,
        now: LocalDateTime,
    ) {
        status = PushCampaignStatus.FAILED
        failureMessage = message.take(500)
        sentAt = now
    }

    /** 누르면 갈 앱 경로. */
    fun path(): String = pathOf(targetType, targetId)

    companion object {
        fun pathOf(
            type: PushTargetType,
            id: Long?,
        ): String =
            when (type) {
                PushTargetType.PRODUCT -> "/products/$id"
                PushTargetType.PROMOTION -> "/promotions/$id"
                PushTargetType.COUPONS -> "/coupons"
                PushTargetType.HOME -> "/"
            }
    }
}

/** 캠페인을 열어 본 사람. 사람마다 한 번만 센다(유니크). */
@Entity
@Table(
    name = "push_campaign_opens",
    uniqueConstraints = [UniqueConstraint(name = "uk_push_campaign_open", columnNames = ["campaign_id", "user_id"])],
)
class PushCampaignOpen(
    @Column(name = "campaign_id", nullable = false)
    val campaignId: Long,
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "opened_at", nullable = false)
    val openedAt: LocalDateTime,
) : IdentityEntity()

/**
 * 앱 알림함 한 줄. 캠페인이 적어도 한 기기에 전달된 사람마다 하나(유니크), 테스트 보내기는 남기지 않는다.
 * 제목·내용은 캠페인에서 읽는다(광고 문구 없이). 시각은 UTC.
 */
@Entity
@Table(
    name = "push_inbox_items",
    uniqueConstraints = [UniqueConstraint(name = "uk_push_inbox_user_campaign", columnNames = ["user_id", "campaign_id"])],
    indexes = [Index(name = "ix_push_inbox_user_created", columnList = "user_id,created_at")],
)
class PushInboxItem(
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "campaign_id", nullable = false)
    val campaignId: Long,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
) : IdentityEntity() {
    @Column(name = "read_at")
    var readAt: LocalDateTime? = null
        protected set
}
