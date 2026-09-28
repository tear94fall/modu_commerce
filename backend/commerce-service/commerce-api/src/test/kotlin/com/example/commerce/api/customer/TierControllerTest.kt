package com.example.commerce.api.customer

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.api.support.OrderRows
import com.example.commerce.api.support.TierClock
import com.example.commerce.application.service.TierRunService
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Import(TierClock.Config::class)
class TierControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val orders: OrderRows,
        private val clock: TierClock,
        private val tierRunService: TierRunService,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        private val jdbc = JdbcTemplate(dataSource)
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() {
            support.reseed()
            clock.setKst(2026, 10, 1, 0, 10)
        }

        private fun coupon(
            name: String,
            active: Boolean = true,
            quantity: String = "null",
        ): Int =
            JsonPath.read(
                mockMvc
                    .post("/api-admin/v1/coupons") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        // 발급 기간은 지났고 받기 노출도 꺼져 있다 — 등급 쿠폰은 둘 다 무시한다.
                        content =
                            """{"name":"$name","discountType":"FIXED","discountValue":3000,"minOrderAmount":0,"scope":"ALL",
                               "issueStart":"2026-01-01","issueEnd":"2026-01-31","validDays":30,"totalQuantity":$quantity,
                               "code":null,"downloadable":false,"active":$active}"""
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )

        private fun tiersBody(
            silver: List<Int> = emptyList(),
            gold: List<Int> = emptyList(),
            vip: List<Int> = emptyList(),
            welcomeMin: Long = 0,
            silverMin: Long = 100_000,
            goldMin: Long = 300_000,
            goldRate: Int = 3,
            vipColor: String = "#7C3AED",
        ) = """[
            {"code":"WELCOME","name":"웰컴","color":"#64748B","minAmount":$welcomeMin,"earnRate":1,"couponIds":[]},
            {"code":"SILVER","name":"실버","color":"#94A3B8","minAmount":$silverMin,"earnRate":2,"couponIds":$silver},
            {"code":"GOLD","name":"골드","color":"#D97706","minAmount":$goldMin,"earnRate":$goldRate,"couponIds":$gold},
            {"code":"VIP","name":"VIP","color":"$vipColor","minAmount":700000,"earnRate":5,"couponIds":$vip}
        ]"""

        private fun putTiers(body: String) =
            mockMvc.put("/api-admin/v1/tiers") {
                with(admin)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        private fun tierOf(userId: String): String =
            requireNotNull(jdbc.queryForObject("select tier_code from commerce_customers where user_id = ?", String::class.java, userId))

        private fun keysOf(
            userId: String,
            couponId: Int,
        ): List<String> =
            jdbc.queryForList(
                "select issue_key from user_coupons where user_id = ? and coupon_id = ? order by issue_key",
                String::class.java,
                userId,
                couponId,
            )

        @Test
        fun `매월 산정은 지난 6개월(KST) 배송 완료 결제 금액으로 등급을 정하고 이력을 남긴다`() {
            listOf("b099", "b100", "b300", "b700", "edgeIn", "edgeOut", "cancel", "points", "coupon").forEach { support.joinCustomer(it) }
            support.joinCustomer("down", tierCode = "GOLD")
            support.joinCustomer("mig", agreed = false, migrated = true)
            support.joinCustomer("gone")
            jdbc.update("update commerce_customers set status = 'WITHDRAWN' where user_id = 'gone'")

            orders.insert("b099", 99_999, TierClock.utc(2026, 6, 15))
            orders.insert("b100", 100_000, TierClock.utc(2026, 6, 15))
            orders.insert("b300", 200_000, TierClock.utc(2026, 5, 1))
            orders.insert("b300", 100_000, TierClock.utc(2026, 8, 31))
            orders.insert("b700", 700_000, TierClock.utc(2026, 9, 1))
            // 기간 경계: 4/1 00:00 KST 와 9/30 23:59:59 KST 는 들어가고, 3/31 23:59:59 와 10/1 00:00 은 빠진다.
            orders.insert("edgeIn", 60_000, TierClock.utc(2026, 4, 1, 0, 0, 0))
            orders.insert("edgeIn", 40_000, TierClock.utc(2026, 9, 30, 23, 59, 59))
            orders.insert("edgeOut", 100_000, TierClock.utc(2026, 3, 31, 23, 59, 59))
            orders.insert("edgeOut", 100_000, TierClock.utc(2026, 10, 1, 0, 0, 0))
            // 취소 주문은 세지 않는다.
            orders.insert("cancel", 150_000, TierClock.utc(2026, 6, 1), status = "CANCELLED")
            orders.insert("cancel", 150_000, TierClock.utc(2026, 6, 2), status = "SHIPPING")
            // 포인트로 낸 금액은 빠진다(쿠폰 할인도).
            orders.insert("points", 130_000, TierClock.utc(2026, 6, 1), points = 40_000)
            orders.insert("coupon", 110_000, TierClock.utc(2026, 6, 1), points = 5_000, coupon = 5_000)
            orders.insert("mig", 300_000, TierClock.utc(2026, 6, 1))
            orders.insert("gone", 700_000, TierClock.utc(2026, 6, 1))

            val runId = requireNotNull(tierRunService.runMonthly())

            mapOf(
                "b099" to "WELCOME",
                "b100" to "SILVER",
                "b300" to "GOLD",
                "b700" to "VIP",
                "edgeIn" to "SILVER",
                "edgeOut" to "WELCOME",
                "cancel" to "WELCOME",
                "points" to "WELCOME",
                "coupon" to "SILVER",
                "down" to "WELCOME",
                "mig" to "GOLD",
                "gone" to "WELCOME",
                "11" to "WELCOME",
            ).forEach { (user, tier) -> assertThat(tierOf(user)).describedAs(user).isEqualTo(tier) }
            assertThat(jdbc.queryForObject("select tier_basis_amount from commerce_customers where user_id = 'b099'", Long::class.java))
                .isEqualTo(99_999)

            val histories =
                jdbc.queryForList(
                    "select user_id, from_code, to_code, basis_amount, period_label, reason, run_id from commerce_tier_histories order by user_id",
                )
            assertThat(histories.map { it["USER_ID"] }).containsExactly("b100", "b300", "b700", "coupon", "down", "edgeIn", "mig")
            val down = histories.first { it["USER_ID"] == "down" }
            assertThat(down["FROM_CODE"]).isEqualTo("GOLD")
            assertThat(down["TO_CODE"]).isEqualTo("WELCOME")
            assertThat(down["REASON"]).isEqualTo("MONTHLY")
            assertThat(down["PERIOD_LABEL"]).isEqualTo("2026.04 ~ 2026.09")
            assertThat((down["RUN_ID"] as Number).toLong()).isEqualTo(runId)

            mockMvc.get("/api-admin/v1/tiers/runs") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].id") { value(runId) }
                jsonPath("$.content[0].reason") { value("MONTHLY") }
                jsonPath("$.content[0].status") { value("DONE") }
                jsonPath("$.content[0].periodLabel") { value("2026.04 ~ 2026.09") }
                // 테스트 고객 6 + 여기서 만든 ACTIVE 11
                jsonPath("$.content[0].customers") { value(CatalogTestSupport.TEST_CUSTOMERS.size + 11) }
                jsonPath("$.content[0].changed") { value(7) }
                jsonPath("$.content[0].countsByTier.SILVER") { value(3) }
                jsonPath("$.content[0].countsByTier.GOLD") { value(2) }
                jsonPath("$.content[0].countsByTier.VIP") { value(1) }
                jsonPath("$.content[0].finishedAt") { exists() }
            }
            mockMvc.get("/api-admin/v1/tiers") { with(admin) }.andExpect {
                jsonPath("$[1].code") { value("SILVER") }
                jsonPath("$[1].customerCount") { value(3) }
                jsonPath("$[0].customerCount") { value(CatalogTestSupport.TEST_CUSTOMERS.size + 5) }
            }
        }

        @Test
        fun `매월 쿠폰은 달마다 한 번, 동의한 고객에게만 주고 꺼진 쿠폰·소진된 쿠폰은 건너뛴다`() {
            val silver = coupon("실버 3천원")
            val gold = coupon("골드 3천원")
            val goldOff = coupon("꺼진 골드 쿠폰", active = false)
            val vip = coupon("VIP 한정", quantity = "1")
            putTiers(tiersBody(silver = listOf(silver), gold = listOf(gold, goldOff), vip = listOf(vip))).andExpect { status { isOk() } }
            // VIP 쿠폰 1장은 이미 나갔다(소진).
            mockMvc
                .post("/api-admin/v1/coupons/$vip/issues") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"userIds":["11"]}"""
                }.andExpect { jsonPath("$.issued") { value(1) } }

            listOf("s1", "s2", "g1", "v1").forEach { support.joinCustomer(it) }
            support.joinCustomer("gm", agreed = false, migrated = true)
            orders.insert("s1", 150_000, TierClock.utc(2026, 7, 1))
            orders.insert("s2", 150_000, TierClock.utc(2026, 4, 20))
            orders.insert("g1", 350_000, TierClock.utc(2026, 7, 1))
            orders.insert("gm", 350_000, TierClock.utc(2026, 7, 1))
            orders.insert("v1", 800_000, TierClock.utc(2026, 7, 1))
            // s1 은 같은 쿠폰을 관리자 지급(once)으로 이미 받았다 — 등급 쿠폰은 발급 키가 달라 또 받는다.
            mockMvc
                .post("/api-admin/v1/coupons/$silver/issues") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"userIds":["s1"]}"""
                }.andExpect { jsonPath("$.issued") { value(1) } }

            tierRunService.runMonthly()

            assertThat(keysOf("s1", silver)).containsExactly("once", "tier:2026-10")
            assertThat(keysOf("s2", silver)).containsExactly("tier:2026-10")
            assertThat(keysOf("g1", gold)).containsExactly("tier:2026-10")
            assertThat(keysOf("g1", goldOff)).isEmpty()
            assertThat(keysOf("gm", gold)).isEmpty()
            assertThat(keysOf("v1", vip)).isEmpty()
            assertThat(
                jdbc.queryForObject("select source from user_coupons where user_id = 's2' and coupon_id = ?", String::class.java, silver),
            ).isEqualTo("TIER")
            val run = jdbc.queryForMap("select coupons_issued, coupons_skipped from commerce_tier_runs")
            assertThat((run["COUPONS_ISSUED"] as Number).toInt()).isEqualTo(3)
            // 꺼진 골드 쿠폰(g1) + 소진된 VIP 쿠폰(v1)
            assertThat((run["COUPONS_SKIPPED"] as Number).toInt()).isEqualTo(2)
            // 앱 쿠폰함에 보인다.
            mockMvc.get("/api-public/v1/me/coupons") { with(jwt().jwt { it.subject("s2") }) }.andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].source") { value("TIER") }
                jsonPath("$[0].name") { value("실버 3천원") }
            }

            // 같은 달에 수동으로 다시 돌려도 또 주지 않는다.
            mockMvc.post("/api-admin/v1/tiers/runs") { with(admin) }.andExpect {
                status { isAccepted() }
                jsonPath("$.reason") { value("MANUAL") }
                jsonPath("$.status") { value("DONE") }
                jsonPath("$.couponsIssued") { value(0) }
                jsonPath("$.changed") { value(0) }
            }
            assertThat(keysOf("s2", silver)).containsExactly("tier:2026-10")

            // 다음 달에는 새 키로 또 받는다(s2 는 4월 주문이 빠져 웰컴으로 내려간다).
            clock.setKst(2026, 11, 1, 0, 10)
            tierRunService.runMonthly()
            assertThat(keysOf("s1", silver)).containsExactly("once", "tier:2026-10", "tier:2026-11")
            assertThat(keysOf("s2", silver)).containsExactly("tier:2026-10")
            assertThat(tierOf("s2")).isEqualTo("WELCOME")
            mockMvc.get("/api-admin/v1/customers/s2") { with(admin) }.andExpect {
                jsonPath("$.tierHistory.length()") { value(2) }
                jsonPath("$.tierHistory[0].fromCode") { value("SILVER") }
                jsonPath("$.tierHistory[0].toCode") { value("WELCOME") }
                jsonPath("$.tierHistory[0].periodLabel") { value("2026.05 ~ 2026.10") }
                jsonPath("$.tierHistory[1].toCode") { value("SILVER") }
            }
        }

        @Test
        fun `산정이 돌고 있으면 수동 실행은 409 이고 매월 실행은 건너뛴다`() {
            jdbc.update(
                "insert into commerce_tier_runs (period_label, reason, started_at, customers, changed, coupons_issued, " +
                    "coupons_skipped, status) values ('2026.04 ~ 2026.09', 'MANUAL', ?, 0, 0, 0, 0, 'RUNNING')",
                Timestamp.valueOf(LocalDateTime.of(2026, 9, 30, 15, 0)),
            )
            mockMvc.post("/api-admin/v1/tiers/runs") { with(admin) }.andExpect {
                status { isConflict() }
                jsonPath("$.message") { value("이미 등급 산정이 진행 중입니다.") }
            }
            assertThat(tierRunService.runMonthly()).isNull()
            assertThat(jdbc.queryForObject("select count(*) from commerce_tier_runs", Long::class.java)).isEqualTo(1)
            mockMvc.post("/api-admin/v1/tiers/runs") { with(jwt().jwt { it.subject("11") }) }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `등급 설정은 코드 4개 전체, 가장 낮은 등급 0원, 기준 금액 오름차순, 적립률 0~20, 있는 쿠폰만`() {
            val c = coupon("골드 쿠폰")
            val missing =
                tiersBody().replace(
                    """{"code":"WELCOME","name":"웰컴","color":"#64748B","minAmount":0,"earnRate":1,"couponIds":[]},""",
                    "",
                )
            putTiers(missing).andExpect { status { isBadRequest() } }
            putTiers(tiersBody(welcomeMin = 1_000)).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("가장 낮은 등급(WELCOME)의 기준 금액은 0원이어야 합니다.") }
            }
            putTiers(tiersBody(goldMin = 100_000)).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("GOLD 의 기준 금액은 SILVER 보다 커야 합니다.") }
            }
            putTiers(tiersBody(goldRate = 21)).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("GOLD: 적립률은 0~20% 사이여야 합니다.") }
            }
            putTiers(tiersBody(vipColor = "purple")).andExpect { status { isBadRequest() } }
            putTiers(tiersBody(gold = listOf(999_999))).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("없는 쿠폰입니다: 999999") }
            }
            putTiers(
                tiersBody().replace("\"VIP\",\"name\":\"VIP\"", "\"PLATINUM\",\"name\":\"VIP\""),
            ).andExpect { status { isBadRequest() } }

            putTiers(tiersBody(gold = listOf(c), goldMin = 250_000, goldRate = 4)).andExpect {
                status { isOk() }
                jsonPath("$[2].code") { value("GOLD") }
                jsonPath("$[2].minAmount") { value(250_000) }
                jsonPath("$[2].earnRate") { value(4) }
                jsonPath("$[2].sortOrder") { value(2) }
                jsonPath("$[2].coupons[0].id") { value(c) }
                jsonPath("$[2].coupons[0].name") { value("골드 쿠폰") }
                jsonPath("$[2].coupons[0].discountLabel") { value("3,000원 할인") }
                jsonPath("$[0].customerCount") { value(CatalogTestSupport.TEST_CUSTOMERS.size) }
            }
            mockMvc.get("/api-public/v1/tiers").andExpect {
                jsonPath("$[2].minAmount") { value(250_000) }
                jsonPath("$[2].monthlyCoupons[0].name") { value("골드 쿠폰") }
                jsonPath("$[2].monthlyCoupons[0].discountLabel") { value("3,000원 할인") }
            }
        }
    }
