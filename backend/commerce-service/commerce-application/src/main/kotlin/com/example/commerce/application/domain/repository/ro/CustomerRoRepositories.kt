package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.config.RoRepository
import com.example.commerce.application.domain.entity.Customer
import com.example.commerce.application.domain.entity.Tier
import com.example.commerce.application.domain.entity.TierHistory
import com.example.commerce.application.domain.entity.TierRun
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 등급별 고객 수 한 줄. */
data class TierCount(
    val tierCode: String,
    val count: Long,
)

interface CustomerRoRepository : RoRepository<Customer, String> {
    fun findByUserId(userId: String): Customer?

    fun findAllByUserIdIn(userIds: Collection<String>): List<Customer>

    /**
     * 백오피스 고객 목록. q 는 회원 id 앞부분, agreed 는 "가입 상태이고 필수 약관 둘 다 동의"(true) 또는 그 반대(false).
     * 가입일 최신 순.
     */
    @Query(
        "select c from Customer c where (:q is null or c.userId like concat(:q, '%')) " +
            "and (:tier is null or c.tierCode = :tier) " +
            "and (:agreed is null " +
            "or (:agreed = true and c.status = 'ACTIVE' and c.termsAgreedAt is not null and c.privacyAgreedAt is not null) " +
            "or (:agreed = false and (c.status <> 'ACTIVE' or c.termsAgreedAt is null or c.privacyAgreedAt is null))) " +
            "order by c.joinedAt desc, c.userId asc",
    )
    fun searchAdmin(
        @Param("q") q: String?,
        @Param("tier") tier: String?,
        @Param("agreed") agreed: Boolean?,
        pageable: Pageable,
    ): Page<Customer>

    @Query(
        "select new com.example.commerce.application.domain.repository.ro.TierCount(c.tierCode, count(c)) " +
            "from Customer c where c.status = 'ACTIVE' group by c.tierCode",
    )
    fun countActiveByTier(): List<TierCount>
}

interface TierRoRepository : RoRepository<Tier, String> {
    fun findAllByOrderBySortOrderAsc(): List<Tier>
}

interface TierHistoryRoRepository : RoRepository<TierHistory, Long> {
    fun findTop24ByUserIdOrderByChangedAtDescIdDesc(userId: String): List<TierHistory>
}

interface TierRunRoRepository : RoRepository<TierRun, Long> {
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<TierRun>

    fun findById(id: Long): TierRun?
}
