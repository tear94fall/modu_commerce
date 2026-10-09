package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.PointOutbox
import com.example.commerce.application.domain.entity.PointOutboxKind
import com.example.commerce.application.domain.entity.PointOutboxStatus
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PointOutboxRoRepository : RoRepository<PointOutbox, Long> {
    fun findByKindAndRefId(
        kind: PointOutboxKind,
        refId: String,
    ): PointOutbox?

    /** 지표(commerce_point_outbox_pending). */
    fun countByStatus(status: PointOutboxStatus): Long

    /** 가장 오래된 행의 생성 시각(UTC). 없으면 null. 지표(commerce_point_outbox_oldest_pending_seconds). */
    @Query("select min(p.createdAt) from PointOutbox p where p.status = :status")
    fun oldestCreatedAt(
        @Param("status") status: PointOutboxStatus,
    ): LocalDateTime?
}
