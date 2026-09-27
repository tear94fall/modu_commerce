package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.PushCampaign
import com.example.commerce.application.domain.entity.PushCampaignOpen
import com.example.commerce.application.domain.entity.PushConsent
import com.example.commerce.application.domain.entity.PushDevice
import com.example.commerce.application.domain.entity.PushInboxItem
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PushDeviceRwRepository : RwRepository<PushDevice, Long> {
    fun findByToken(token: String): PushDevice?

    fun findAllByUserIdIn(userIds: Collection<String>): List<PushDevice>

    @Modifying
    @Query("delete from PushDevice d where d.userId = :userId and d.token = :token")
    fun deleteMine(
        @Param("userId") userId: String,
        @Param("token") token: String,
    ): Int

    /** 회원 탈퇴. */
    @Modifying
    @Query("delete from PushDevice d where d.userId = :userId")
    fun deleteAllOfUser(
        @Param("userId") userId: String,
    ): Int

    @Modifying
    @Query("delete from PushDevice d where d.token in :tokens")
    fun deleteTokens(
        @Param("tokens") tokens: Collection<String>,
    ): Int

    /** 지금 보낼 수 있는 기기: 혜택 알림 동의자(야간이면 야간 동의자까지)의 기기. */
    @Query(
        "select d from PushDevice d where d.userId in " +
            "(select c.userId from PushConsent c where c.marketing = true and (:night = false or c.night = true)) order by d.id",
    )
    fun findEligible(
        @Param("night") night: Boolean,
    ): List<PushDevice>
}

interface PushConsentRwRepository : RwRepository<PushConsent, String> {
    /** 회원 탈퇴. */
    @Modifying
    @Query("delete from PushConsent c where c.userId = :userId")
    fun deleteOfUser(
        @Param("userId") userId: String,
    ): Int
}

interface PushCampaignRwRepository : RwRepository<PushCampaign, Long> {
    /** 보낼 때가 된 예약 캠페인. */
    @Query("select c.id from PushCampaign c where c.status = 'SCHEDULED' and c.scheduledAt <= :now order by c.scheduledAt, c.id")
    fun findDueIds(
        @Param("now") now: LocalDateTime,
    ): List<Long>

    /** 보내기 선점. 1 이면 이 호출이 이겼다(스케줄러·지금 보내기가 겹쳐도 한 번만 보낸다). */
    @Modifying
    @Query("update PushCampaign c set c.status = 'SENDING' where c.id = :id and c.status = 'SCHEDULED'")
    fun claim(
        @Param("id") id: Long,
    ): Int

    /** 예약 취소. 보내기가 먼저 선점했으면 0. */
    @Modifying
    @Query("update PushCampaign c set c.status = 'CANCELED', c.canceledAt = :now where c.id = :id and c.status = 'SCHEDULED'")
    fun cancel(
        @Param("id") id: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying
    @Query("update PushCampaign c set c.openedCount = c.openedCount + 1 where c.id = :id")
    fun incrementOpened(
        @Param("id") id: Long,
    ): Int
}

interface PushCampaignOpenRwRepository : RwRepository<PushCampaignOpen, Long> {
    fun existsByCampaignIdAndUserId(
        campaignId: Long,
        userId: String,
    ): Boolean
}

interface PushInboxItemRwRepository : RwRepository<PushInboxItem, Long> {
    fun existsByIdAndUserId(
        id: Long,
        userId: String,
    ): Boolean

    @Modifying
    @Query("update PushInboxItem i set i.readAt = :now where i.id = :id and i.userId = :userId and i.readAt is null")
    fun markRead(
        @Param("id") id: Long,
        @Param("userId") userId: String,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying
    @Query("update PushInboxItem i set i.readAt = :now where i.userId = :userId and i.readAt is null")
    fun markAllRead(
        @Param("userId") userId: String,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying
    @Query(
        "update PushInboxItem i set i.readAt = :now where i.userId = :userId and i.campaignId = :campaignId and i.readAt is null",
    )
    fun markCampaignRead(
        @Param("userId") userId: String,
        @Param("campaignId") campaignId: Long,
        @Param("now") now: LocalDateTime,
    ): Int
}
