package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.PointOutbox
import com.example.commerce.application.domain.entity.PointOutboxKind
import com.example.commerce.application.domain.entity.PointOutboxStatus
import java.time.LocalDateTime

interface PointOutboxRwRepository : RwRepository<PointOutbox, Long> {
    fun findByKindAndRefId(
        kind: PointOutboxKind,
        refId: String,
    ): PointOutbox?

    /** 릴레이 대상: 지금 보낼 PENDING 을 오래된 순으로 최대 100건. */
    fun findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        status: PointOutboxStatus,
        now: LocalDateTime,
    ): List<PointOutbox>
}
