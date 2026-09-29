package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.PushCampaign
import com.example.commerce.application.domain.entity.PushCampaignOpen
import com.example.commerce.application.domain.entity.PushCampaignStatus
import com.example.commerce.application.domain.entity.PushConsent
import com.example.commerce.application.domain.entity.PushDevice
import com.example.commerce.application.domain.entity.PushPlatform
import com.example.commerce.application.domain.entity.PushTargetType
import com.example.commerce.application.domain.repository.ro.PushCampaignRoRepository
import com.example.commerce.application.domain.repository.ro.PushConsentRoRepository
import com.example.commerce.application.domain.repository.ro.PushDeviceRoRepository
import com.example.commerce.application.domain.repository.ro.PushInboxItemRoRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.domain.repository.rw.PromotionRwRepository
import com.example.commerce.application.domain.repository.rw.PushCampaignOpenRwRepository
import com.example.commerce.application.domain.repository.rw.PushCampaignRwRepository
import com.example.commerce.application.domain.repository.rw.PushConsentRwRepository
import com.example.commerce.application.domain.repository.rw.PushDeviceRwRepository
import com.example.commerce.application.domain.repository.rw.PushInboxItemRwRepository
import com.example.commerce.application.push.PushMessage
import com.example.commerce.application.push.PushRules
import com.example.commerce.application.push.PushSender
import com.example.commerce.application.usecase.command.PushCampaignCommand
import com.example.commerce.application.usecase.command.PushContentCommand
import com.example.commerce.application.usecase.result.NotificationItemResult
import com.example.commerce.application.usecase.result.PushAudienceResult
import com.example.commerce.application.usecase.result.PushConsentResult
import com.example.commerce.application.usecase.result.PushTestResult
import jakarta.persistence.EntityNotFoundException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import javax.sql.DataSource

/** 예약 상태가 아니라 취소할 수 없다(409). */
class PushCampaignStateException(
    message: String,
) : RuntimeException(message)

/** 테스트 푸시를 보내지 못했다(503). */
class PushSendFailedException(
    cause: Throwable,
) : RuntimeException("푸시를 보내지 못했습니다: ${cause.message ?: cause.javaClass.simpleName}", cause)

@Service
@Transactional(transactionManager = "rwTransactionManager")
class PushDeviceService(
    private val pushDeviceRwRepository: PushDeviceRwRepository,
    private val clock: Clock,
) {
    /** 등록하거나(처음 본 토큰) 마지막 확인 시각을 고친다. 다른 사람의 토큰이면 이 사람에게 옮긴다. */
    fun register(
        userId: String,
        token: String,
        platform: PushPlatform,
    ) {
        require(token.isNotBlank() && token.length <= 512) { "푸시 토큰이 올바르지 않습니다." }
        val now = PushRules.utc(clock.instant())
        val device = pushDeviceRwRepository.findByToken(token)
        if (device != null) {
            device.seen(userId, platform, now)
        } else {
            pushDeviceRwRepository.saveAndFlush(PushDevice(userId, token, platform, now))
        }
    }

    /** 내 토큰일 때만 지운다(로그아웃). */
    fun remove(
        userId: String,
        token: String,
    ) {
        pushDeviceRwRepository.deleteMine(userId, token)
    }

    fun eligible(night: Boolean): List<PushDevice> = pushDeviceRwRepository.findEligible(night)

    fun devicesOf(userIds: Collection<String>): List<PushDevice> =
        if (userIds.isEmpty()) emptyList() else pushDeviceRwRepository.findAllByUserIdIn(userIds)

    /** FCM 이 더는 못 쓴다고 알려 준 토큰을 지우고 지운 수를 돌려준다. */
    fun deleteTokens(tokens: Collection<String>): Int = if (tokens.isEmpty()) 0 else pushDeviceRwRepository.deleteTokens(tokens)
}

/**
 * 내 수신 동의 조회는 master 에서 한다(읽기 전용 트랜잭션). 가입(혜택 알림 동의)·설정 변경 직후 알림 설정 화면이 다시 읽는데,
 * 레플리카 지연 동안 옛 동의(꺼짐)가 보이면 사용자가 다시 켜려다 두 번 바꾸게 된다. 한 사람의 한 줄 조회라 부담은 작다.
 */
@Service
@Transactional(transactionManager = "rwTransactionManager", readOnly = true)
class PushConsentQueryService(
    private val pushConsentRwRepository: PushConsentRwRepository,
) {
    fun get(userId: String): PushConsentResult =
        pushConsentRwRepository.findByIdOrNull(userId)?.let(PushConsentResult::from) ?: PushConsentResult.DEFAULT
}

@Service
@Transactional(transactionManager = "rwTransactionManager")
class PushConsentCommandService(
    private val pushConsentRwRepository: PushConsentRwRepository,
    private val clock: Clock,
) {
    fun change(
        userId: String,
        marketing: Boolean,
        night: Boolean,
    ): PushConsentResult {
        val consent = pushConsentRwRepository.findByIdOrNull(userId) ?: PushConsent(userId)
        consent.change(marketing, night, PushRules.utc(clock.instant()))
        return PushConsentResult.from(pushConsentRwRepository.saveAndFlush(consent))
    }
}

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class PushCampaignQueryService(
    private val pushCampaignRoRepository: PushCampaignRoRepository,
    private val pushConsentRoRepository: PushConsentRoRepository,
    private val pushDeviceRoRepository: PushDeviceRoRepository,
    private val clock: Clock,
) {
    fun find(id: Long): PushCampaign = pushCampaignRoRepository.findById(id) ?: throw notFound(id)

    fun adminPage(
        status: PushCampaignStatus?,
        q: String?,
        page: Int,
        size: Int,
    ): Page<PushCampaign> =
        pushCampaignRoRepository.searchAdmin(
            status,
            q?.trim()?.takeIf { it.isNotEmpty() },
            PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)),
        )

    /** [at](없으면 지금)에 보내면 받을 사람 수. */
    fun audience(at: Instant?): PushAudienceResult {
        val night = PushRules.isNight(at ?: clock.instant())
        return PushAudienceResult(
            night = night,
            users = pushDeviceRoRepository.countEligibleUsers(night),
            devices = pushDeviceRoRepository.countEligibleDevices(night),
            consentedUsers = pushConsentRoRepository.countConsented(false),
            nightUsers = pushConsentRoRepository.countConsented(true),
        )
    }

    companion object {
        fun notFound(id: Long) = EntityNotFoundException("id: $id 에 해당하는 푸시 캠페인이 없습니다.")
    }
}

@Service
@Transactional(transactionManager = "rwTransactionManager")
class PushCampaignCommandService(
    private val pushCampaignRwRepository: PushCampaignRwRepository,
    private val pushCampaignOpenRwRepository: PushCampaignOpenRwRepository,
    private val productRwRepository: ProductRwRepository,
    private val promotionRwRepository: PromotionRwRepository,
    private val clock: Clock,
) {
    fun create(command: PushCampaignCommand): PushCampaign {
        val content = command.content
        content.validate()
        val label = targetLabel(content)
        val now = clock.instant()
        val at = command.scheduledAt?.toInstant() ?: now
        require(!at.isBefore(now.minus(SCHEDULE_GRACE))) { "예약 시각이 이미 지났습니다." }
        require(!at.isAfter(now.plus(MAX_AHEAD))) { "예약은 30일 이내로만 할 수 있습니다." }
        val targetId =
            if (content.targetType == PushTargetType.PRODUCT ||
                content.targetType == PushTargetType.PROMOTION
            ) {
                content.targetId
            } else {
                null
            }
        return pushCampaignRwRepository.saveAndFlush(
            PushCampaign(
                title = content.title,
                body = content.body,
                imageUrl = content.imageUrl,
                targetType = content.targetType,
                targetId = targetId,
                targetLabel = label,
                scheduledAt = PushRules.utc(at),
                createdBy = command.createdBy,
                createdAt = PushRules.utc(now),
            ),
        )
    }

    /** 대상을 검사하고 이름 스냅숏을 돌려준다. 판매 중인 상품, 노출 중인 기획전·이벤트만 고를 수 있다. */
    fun targetLabel(content: PushContentCommand): String? =
        when (content.targetType) {
            PushTargetType.PRODUCT -> {
                val product =
                    productRwRepository.findByIdOrNull(requireNotNull(content.targetId)) ?: throw IllegalArgumentException("없는 상품입니다.")
                require(product.status == ProductStatus.SELLING) { "판매 중인 상품만 고를 수 있습니다." }
                product.name
            }
            PushTargetType.PROMOTION -> {
                val promotion =
                    promotionRwRepository.findByIdOrNull(requireNotNull(content.targetId))
                        ?: throw IllegalArgumentException("없는 기획전·이벤트입니다.")
                require(promotion.visible) { "노출 중인 기획전·이벤트만 고를 수 있습니다." }
                promotion.title
            }
            PushTargetType.COUPONS, PushTargetType.HOME -> null
        }

    fun cancel(id: Long) {
        if (pushCampaignRwRepository.cancel(id, PushRules.utc(clock.instant())) == 0) {
            val campaign = pushCampaignRwRepository.findByIdOrNull(id) ?: throw PushCampaignQueryService.notFound(id)
            throw PushCampaignStateException("예약 상태인 캠페인만 취소할 수 있습니다. (지금: ${campaign.status})")
        }
    }

    fun dueIds(): List<Long> = pushCampaignRwRepository.findDueIds(PushRules.utc(clock.instant()))

    fun claim(id: Long): Boolean = pushCampaignRwRepository.claim(id) == 1

    /** master 에서 읽는다(보내기 단계, 만들기·취소 응답). */
    @Transactional(transactionManager = "rwTransactionManager", readOnly = true)
    fun find(id: Long): PushCampaign = pushCampaignRwRepository.findByIdOrNull(id) ?: throw PushCampaignQueryService.notFound(id)

    fun started(
        id: Long,
        night: Boolean,
        users: Int,
        devices: Int,
    ) = find(id).started(night, users, devices)

    fun progress(
        id: Long,
        success: Int,
        failure: Int,
        removed: Int,
    ) = find(id).progress(success, failure, removed)

    fun sent(id: Long) = find(id).sent(PushRules.utc(clock.instant()))

    fun failed(
        id: Long,
        message: String,
    ) = find(id).failed(message, PushRules.utc(clock.instant()))

    /** 열어 봄을 사람마다 한 번 센다. 없는 캠페인이면 무시. 동시에 두 번 오면 유니크 제약이 하나만 남긴다(호출한 쪽이 무시). */
    fun recordOpen(
        id: Long,
        userId: String,
    ) {
        if (!pushCampaignRwRepository.existsById(id)) return
        if (pushCampaignOpenRwRepository.existsByCampaignIdAndUserId(id, userId)) return
        pushCampaignOpenRwRepository.saveAndFlush(PushCampaignOpen(id, userId, PushRules.utc(clock.instant())))
        pushCampaignRwRepository.incrementOpened(id)
    }

    companion object {
        private val SCHEDULE_GRACE: Duration = Duration.ofMinutes(1)
        private val MAX_AHEAD: Duration = Duration.ofDays(30)
    }
}

/**
 * 캠페인 보내기. 트랜잭션 없이 단계마다 짧은 쓰기 트랜잭션을 쓴다(FCM 호출 동안 DB 연결을 잡지 않는다).
 * 선점([PushCampaignCommandService.claim])에 이긴 호출만 보내므로 스케줄러와 "지금 보내기"가 겹쳐도 한 번만 나간다.
 */
@Service
class PushCampaignSendService(
    private val pushCampaignCommandService: PushCampaignCommandService,
    private val pushDeviceService: PushDeviceService,
    private val pushInboxCommandService: PushInboxCommandService,
    private val pushSender: PushSender,
    private val clock: Clock,
) {
    /** 보낼 때가 된 예약 캠페인을 모두 보낸다(스케줄러가 1분마다 부른다). 이 호출이 보낸 수를 돌려준다. */
    fun runDue(): Int = pushCampaignCommandService.dueIds().count { send(it) }

    /** 선점에 이기면 보내고 true. 보내다 실패하면 FAILED(그때까지의 수는 남는다). */
    fun send(id: Long): Boolean {
        if (!pushCampaignCommandService.claim(id)) return false
        try {
            val campaign = pushCampaignCommandService.find(id)
            val night = PushRules.isNight(clock.instant())
            val devices = pushDeviceService.eligible(night)
            pushCampaignCommandService.started(id, night, devices.map { it.userId }.distinct().size, devices.size)
            val message =
                PushMessage(
                    title = PushRules.title(campaign.title),
                    body = PushRules.body(campaign.body),
                    imageUrl = campaign.imageUrl,
                    data = mapOf("type" to "campaign", "campaignId" to id.toString(), "path" to campaign.path()),
                )
            var success = 0
            var failure = 0
            var removed = 0
            val owners = devices.associate { it.token to it.userId }
            devices.map { it.token }.chunked(PushRules.CHUNK_SIZE).forEach { chunk ->
                val result = pushSender.send(chunk, message)
                success += result.success
                failure += result.failure
                removed += pushDeviceService.deleteTokens(result.invalidTokens)
                // 전달된 기기의 주인에게 알림함 줄을 남긴다(청크마다 — 중간에 실패해도 받은 사람의 줄은 남는다).
                pushInboxCommandService.add(id, result.successTokens.mapNotNull { owners[it] })
                pushCampaignCommandService.progress(id, success, failure, removed)
            }
            pushCampaignCommandService.sent(id)
            logger.info {
                "push campaign $id sent: devices=${devices.size} success=$success failure=$failure removed=$removed night=$night"
            }
        } catch (e: Exception) {
            logger.error(e) { "push campaign $id failed" }
            pushCampaignCommandService.failed(id, e.message ?: e.javaClass.simpleName)
        }
        return true
    }

    /** 테스트 보내기: 동의와 상관없이 고른 회원(1~5명)의 기기로. 캠페인으로 남기지 않는다. */
    fun test(
        content: PushContentCommand,
        userIds: List<String>,
    ): PushTestResult {
        content.validate()
        val ids = userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(ids.size in 1..5) { "테스트 받을 회원을 1~5명 고르세요." }
        pushCampaignCommandService.targetLabel(content)
        val devices = pushDeviceService.devicesOf(ids)
        val targetId =
            if (content.targetType == PushTargetType.PRODUCT ||
                content.targetType == PushTargetType.PROMOTION
            ) {
                content.targetId
            } else {
                null
            }
        val message =
            PushMessage(
                title = PushRules.TEST_PREFIX + PushRules.title(content.title),
                body = PushRules.body(content.body),
                imageUrl = content.imageUrl,
                data = mapOf("type" to "campaign", "path" to PushCampaign.pathOf(content.targetType, targetId)),
            )
        var success = 0
        var failure = 0
        try {
            devices.map { it.token }.chunked(PushRules.CHUNK_SIZE).forEach { chunk ->
                val result = pushSender.send(chunk, message)
                success += result.success
                failure += result.failure
                pushDeviceService.deleteTokens(result.invalidTokens)
            }
        } catch (e: Exception) {
            throw PushSendFailedException(e)
        }
        val owners = devices.map { it.userId }.toSet()
        return PushTestResult(owners.size, devices.size, success, failure, ids.filter { it !in owners })
    }
}

/** 앱 알림함 쓰기. */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class PushInboxCommandService(
    private val pushInboxItemRwRepository: PushInboxItemRwRepository,
    @Qualifier("rwDataSource") dataSource: DataSource,
    private val clock: Clock,
) {
    private val jdbc = JdbcTemplate(dataSource)

    /**
     * 캠페인을 받은 사람들의 알림함 줄을 넣는다. 수십만 명이 될 수 있어 여러 행 INSERT 한 문장씩(500명) 넣고,
     * 이미 있는 줄(같은 사람의 다른 기기, 다시 보내기)은 유니크 제약으로 건너뛴다(INSERT IGNORE). 넣은 수를 돌려준다.
     */
    fun add(
        campaignId: Long,
        userIds: Collection<String>,
    ): Int {
        // JPA 로 쓰는 다른 시각과 같게 Timestamp 로 넘긴다. LocalDateTime 을 그대로 넘기면 드라이버가
        // serverTimezone 변환을 건너뛰어, JPA 로 읽을 때 9시간 어긋난다.
        val now = Timestamp.valueOf(PushRules.utc(clock.instant()))
        return userIds.distinct().chunked(PushRules.CHUNK_SIZE).sumOf { chunk ->
            val sql =
                "insert ignore into push_inbox_items (user_id, campaign_id, created_at) values " +
                    chunk.joinToString(",") { "(?, ?, ?)" }
            val args = chunk.flatMap { listOf(it, campaignId, now) }.toTypedArray()
            jdbc.update(sql, *args)
        }
    }

    /** 내 줄만 읽음으로. 남의 줄·없는 줄은 404. 이미 읽었으면 그대로. */
    fun markRead(
        userId: String,
        id: Long,
    ) {
        if (!pushInboxItemRwRepository.existsByIdAndUserId(id, userId)) throw notFound(id)
        pushInboxItemRwRepository.markRead(id, userId, PushRules.utc(clock.instant()))
    }

    fun markAllRead(userId: String) {
        pushInboxItemRwRepository.markAllRead(userId, PushRules.utc(clock.instant()))
    }

    /**
     * 상단바 뱃지의 안 읽은 수. master 에서 센다 — 알림을 눌러 읽음으로 바꾼 직후(또는 새 푸시를 받은 직후) 뱃지가 다시 세는데
     * 레플리카 지연 동안 옛 수가 보이면 읽은 알림이 그대로 남아 보인다. 한 사람의 30일치 count 라 부담은 작다.
     */
    @Transactional(transactionManager = "rwTransactionManager", readOnly = true)
    fun unread(userId: String): Long =
        pushInboxItemRwRepository.countByUserIdAndCreatedAtGreaterThanEqualAndReadAtIsNull(userId, PushInboxQueryService.since(clock))

    /** 알림을 눌러 열었으면 그 캠페인의 내 줄도 읽음으로(없으면 그만). */
    fun markCampaignRead(
        userId: String,
        campaignId: Long,
    ) {
        pushInboxItemRwRepository.markCampaignRead(userId, campaignId, PushRules.utc(clock.instant()))
    }

    companion object {
        fun notFound(id: Long) = EntityNotFoundException("id: $id 에 해당하는 알림이 없습니다.")
    }
}

/** 앱 알림함 읽기. 받은 지 30일(주입한 시계 기준) 안쪽만 보여 준다. */
@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class PushInboxQueryService(
    private val pushInboxItemRoRepository: PushInboxItemRoRepository,
    private val pushCampaignRoRepository: PushCampaignRoRepository,
    private val clock: Clock,
) {
    fun page(
        userId: String,
        page: Int,
        size: Int,
    ): Page<NotificationItemResult> {
        val pageable =
            PageRequest.of(
                maxOf(page, 0),
                size.coerceIn(1, MAX_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
            )
        val items = pushInboxItemRoRepository.findByUserIdAndCreatedAtGreaterThanEqual(userId, since(), pageable)
        val campaigns =
            if (items.isEmpty) {
                emptyMap()
            } else {
                pushCampaignRoRepository.findAllByIdIn(items.content.map { it.campaignId }.toSet()).associateBy { it.id }
            }
        // 캠페인은 지우지 않지만, 혹시 없으면 그 줄은 건너뛴다(전체 수는 그대로).
        val content = items.content.mapNotNull { item -> campaigns[item.campaignId]?.let { NotificationItemResult.of(item, it) } }
        return PageImpl(content, pageable, items.totalElements)
    }

    private fun since() = since(clock)

    companion object {
        const val MAX_SIZE = 50
        private val KEEP: Duration = Duration.ofDays(30)

        /** 알림함에 보이는 가장 오래된 받은 시각(UTC). */
        fun since(clock: Clock): LocalDateTime = PushRules.utc(clock.instant().minus(KEEP))
    }
}
