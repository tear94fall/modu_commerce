package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.PushCampaign
import com.example.commerce.application.domain.entity.PushCampaignStatus
import com.example.commerce.application.domain.entity.PushConsent
import com.example.commerce.application.domain.entity.PushDevice
import com.example.commerce.application.domain.entity.PushInboxItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PushConsentRoRepository : RoRepository<PushConsent, String> {
    @Query("select count(c) from PushConsent c where c.marketing = true and (:night = false or c.night = true)")
    fun countConsented(
        @Param("night") night: Boolean,
    ): Long
}

interface PushDeviceRoRepository : RoRepository<PushDevice, Long> {
    @Query(
        "select count(d) from PushDevice d where d.userId in " +
            "(select c.userId from PushConsent c where c.marketing = true and (:night = false or c.night = true))",
    )
    fun countEligibleDevices(
        @Param("night") night: Boolean,
    ): Long

    @Query(
        "select count(distinct d.userId) from PushDevice d where d.userId in " +
            "(select c.userId from PushConsent c where c.marketing = true and (:night = false or c.night = true))",
    )
    fun countEligibleUsers(
        @Param("night") night: Boolean,
    ): Long
}

interface PushCampaignRoRepository : RoRepository<PushCampaign, Long> {
    fun findById(id: Long): PushCampaign?

    fun findAllByIdIn(ids: Collection<Long>): List<PushCampaign>

    /** 백오피스 목록. 상태·제목(부분 일치)으로 거르고 보낼 시각 최신 순. */
    @Query(
        "select c from PushCampaign c where (:status is null or c.status = :status) " +
            "and (:q is null or lower(c.title) like lower(concat('%', :q, '%'))) order by c.scheduledAt desc, c.id desc",
    )
    fun searchAdmin(
        @Param("status") status: PushCampaignStatus?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<PushCampaign>
}

interface PushInboxItemRoRepository : RoRepository<PushInboxItem, Long> {
    /** 내 알림함([since] 이후 받은 것). 정렬은 Pageable 로(받은 시각 최신 순). */
    fun findByUserIdAndCreatedAtGreaterThanEqual(
        userId: String,
        since: LocalDateTime,
        pageable: Pageable,
    ): Page<PushInboxItem>
}
