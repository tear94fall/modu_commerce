package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Customer
import com.example.commerce.application.domain.entity.CustomerStatus
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.domain.entity.TierHistory
import com.example.commerce.application.domain.entity.TierRun
import com.example.commerce.application.domain.entity.TierRunStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CustomerRwRepository : RwRepository<Customer, String> {
    fun findAllByStatus(status: CustomerStatus): List<Customer>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.userId = :userId")
    fun findForUpdate(
        @Param("userId") userId: String,
    ): Customer?
}

interface TierRwRepository : RwRepository<Tier, String> {
    fun findAllByOrderBySortOrderAsc(): List<Tier>
}

interface TierHistoryRwRepository : RwRepository<TierHistory, Long>

interface TierRunRwRepository : RwRepository<TierRun, Long> {
    fun existsByStatus(status: TierRunStatus): Boolean
}
