package com.example.commerce.api.customer

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.api.support.OrderRows
import com.example.commerce.api.support.TierClock
import com.example.commerce.application.seed.CustomerMigration
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Import(TierClock.Config::class)
class CustomerControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val orders: OrderRows,
        private val clock: TierClock,
        private val customerMigration: CustomerMigration,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        private val jdbc = JdbcTemplate(dataSource)
        private val stranger = jwt().jwt { it.subject("99") }
        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() {
            support.reseed()
            clock.setKst(2026, 9, 28)
        }

        private fun join(
            user: String,
            body: String = """{"agreeTerms":true,"agreePrivacy":true}""",
        ) = mockMvc.post("/api-public/v1/me/customer") {
            with(jwt().jwt { it.subject(user) })
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

        @Test
        fun `가입 전에는 보호된 API 가 403 CUSTOMER_REQUIRED 이고 둘러보기는 열려 있다`() {
            val productId = support.productId("모두 머그컵 세트")
            listOf(
                "/api-public/v1/cart",
                "/api-public/v1/orders",
                "/api-public/v1/wishlist",
                "/api-public/v1/addresses",
                "/api-public/v1/me/coupons",
                "/api-public/v1/me/coupons/count",
                "/api-public/v1/me/reviews",
                "/api-public/v1/me/notifications",
                "/api-public/v1/me/notifications/unread-count",
            ).forEach { path ->
                mockMvc.get(path) { with(stranger) }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.message") { value("모두의 커머스 가입이 필요합니다") }
                    jsonPath("$.code") { value("CUSTOMER_REQUIRED") }
                }
            }
            mockMvc
                .post("/api-public/v1/wishlist/$productId") { with(stranger) }
                .andExpect { status { isForbidden() } }
            mockMvc
                .put("/api-public/v1/me/push/devices") {
                    with(stranger)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"token":"t-99","platform":"ANDROID"}"""
                }.andExpect { status { isForbidden() } }
            mockMvc
                .put("/api-public/v1/me/push/consent") {
                    with(stranger)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":true,"night":false}"""
                }.andExpect { status { isForbidden() } }

            listOf(
                "/api-public/v1/products",
                "/api-public/v1/products/$productId",
                "/api-public/v1/categories",
                "/api-public/v1/promotions/banners",
                "/api-public/v1/products/$productId/reviews",
                "/api-public/v1/coupons/downloadable",
                "/api-public/v1/me/push/consent",
            ).forEach { path -> mockMvc.get(path) { with(stranger) }.andExpect { status { isOk() } } }
            // 로그아웃 때 기기 해제는 가입 전에도 된다.
            mockMvc.delete("/api-public/v1/me/push/devices?token=t-99") { with(stranger) }.andExpect { status { isNoContent() } }
        }

        @Test
        fun `GET me customer 는 가입 전·동의 전·탈퇴면 404 CUSTOMER_REQUIRED`() {
            mockMvc.get("/api-public/v1/me/customer") { with(stranger) }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("CUSTOMER_REQUIRED") }
            }
            support.joinCustomer("55", agreed = false, migrated = true)
            mockMvc.get("/api-public/v1/me/customer") { with(jwt().jwt { it.subject("55") }) }.andExpect { status { isNotFound() } }
            jdbc.update("update commerce_customers set status = 'WITHDRAWN' where user_id = '11'")
            mockMvc.get("/api-public/v1/me/customer") { with(me) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api-public/v1/cart") { with(me) }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `가입은 필수 약관 둘 다 동의해야 하고 가입하면 보호된 API 를 쓸 수 있다`() {
            join("99", """{"agreeTerms":true,"agreePrivacy":false}""").andExpect { status { isBadRequest() } }
            join("99", """{"agreeTerms":true}""").andExpect { status { isBadRequest() } }
            join("99", """{}""").andExpect { status { isBadRequest() } }
            mockMvc.get("/api-public/v1/me/customer") { with(stranger) }.andExpect { status { isNotFound() } }

            join("99").andExpect {
                status { isCreated() }
                jsonPath("$.userId") { value("99") }
                jsonPath("$.termsVersion") { value("2026-10") }
                jsonPath("$.termsAgreedAt") { exists() }
                jsonPath("$.privacyAgreedAt") { exists() }
                jsonPath("$.tier.code") { value("WELCOME") }
                jsonPath("$.tier.name") { value("웰컴") }
                jsonPath("$.tier.color") { value("#0EA5E9") }
                jsonPath("$.tier.earnRate") { value(1) }
                jsonPath("$.tier.minAmount") { value(0) }
                jsonPath("$.basisAmount") { value(0) }
                jsonPath("$.rolling.amount") { value(0) }
                jsonPath("$.rolling.expectedTier.code") { value("WELCOME") }
                jsonPath("$.rolling.nextTier.code") { value("SILVER") }
                jsonPath("$.rolling.amountToNext") { value(100_000) }
                jsonPath("$.periodLabel") { value("2026.03 ~ 2026.08") }
            }
            mockMvc.get("/api-public/v1/cart") { with(stranger) }.andExpect { status { isOk() } }
            mockMvc.get("/api-public/v1/me/customer") { with(stranger) }.andExpect { status { isOk() } }
            // 혜택 알림을 고르지 않았으면 동의를 건드리지 않는다.
            mockMvc.get("/api-public/v1/me/push/consent") { with(stranger) }.andExpect { jsonPath("$.marketing") { value(false) } }
        }

        @Test
        fun `혜택 알림에 동의하고 가입하면 광고성 푸시 동의가 켜진다(야간 제외)`() {
            join("98", """{"agreeTerms":true,"agreePrivacy":true,"marketing":true}""").andExpect { status { isCreated() } }
            mockMvc.get("/api-public/v1/me/push/consent") { with(jwt().jwt { it.subject("98") }) }.andExpect {
                jsonPath("$.marketing") { value(true) }
                jsonPath("$.night") { value(false) }
            }
        }

        @Test
        fun `옮겨 온 회원이 동의하면 데이터는 그대로 두고 지난 6개월 금액으로 등급을 정한다`() {
            support.joinCustomer("55", agreed = false, migrated = true)
            // 기준 기간(2026.03 ~ 2026.08) 안 120,000원 + 이번 달 50,000원(누적에만 들어간다)
            orders.insert("55", 120_000, TierClock.utc(2026, 5, 10))
            orders.insert("55", 50_000, TierClock.utc(2026, 9, 2))
            join("55").andExpect {
                status { isCreated() }
                jsonPath("$.tier.code") { value("SILVER") }
                jsonPath("$.basisAmount") { value(120_000) }
                jsonPath("$.rolling.amount") { value(170_000) }
                jsonPath("$.rolling.expectedTier.code") { value("SILVER") }
                jsonPath("$.rolling.nextTier.code") { value("GOLD") }
                jsonPath("$.rolling.amountToNext") { value(130_000) }
                jsonPath("$.joinedAt") { value("2026-09-01T00:00:00") }
            }
            mockMvc.get("/api-admin/v1/customers/55") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.migrated") { value(true) }
                jsonPath("$.tier.code") { value("SILVER") }
                jsonPath("$.rollingAmount") { value(170_000) }
                jsonPath("$.tierHistory.length()") { value(1) }
                jsonPath("$.tierHistory[0].fromCode") { value("WELCOME") }
                jsonPath("$.tierHistory[0].toCode") { value("SILVER") }
                jsonPath("$.tierHistory[0].reason") { value("JOIN") }
                jsonPath("$.tierHistory[0].periodLabel") { value("2026.03 ~ 2026.08") }
            }
        }

        @Test
        fun `기존 데이터의 회원을 동의 전 고객으로 옮기고 두 번 돌려도 그대로다`() {
            val early = Timestamp.valueOf(LocalDateTime.of(2026, 3, 1, 9, 0))
            val later = Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 9, 0))
            jdbc.update(
                "insert into addresses (created_at, updated_at, user_id, recipient, phone, zip_code, address1, is_default) " +
                    "values (?, ?, '77', '홍길동', '010-1111-2222', '06236', '서울', true)",
                later,
                later,
            )
            jdbc.update(
                "insert into push_consents (user_id, marketing, marketing_updated_at, night) values ('76', true, ?, false)",
                later,
            )
            val orderId = orders.insert("77", 10_000, null, status = "PAID")
            jdbc.update("update orders set created_at = ?, updated_at = ? where id = ?", early, early, orderId)
            val delivered = orders.insert("75", 10_000, null)
            jdbc.update("update orders set delivered_at = null, updated_at = ? where id = ?", later, delivered)

            customerMigration.run(org.springframework.boot.DefaultApplicationArguments())

            val rows =
                jdbc.queryForList(
                    "select user_id, status, migrated, terms_agreed_at, tier_code, joined_at from commerce_customers " +
                        "where user_id in ('75', '76', '77') order by user_id",
                )
            assertThat(rows.map { it["USER_ID"] }).containsExactly("75", "76", "77")
            rows.forEach {
                assertThat(it["STATUS"]).isEqualTo("ACTIVE")
                assertThat(it["MIGRATED"]).isEqualTo(true)
                assertThat(it["TERMS_AGREED_AT"]).isNull()
                assertThat(it["TIER_CODE"]).isEqualTo("WELCOME")
            }
            assertThat(rows[2]["JOINED_AT"]).isEqualTo(early)
            assertThat(rows[1]["JOINED_AT"]).isEqualTo(later)
            // 배송 완료 주문의 delivered_at 은 updated_at 으로 채운다.
            assertThat(
                jdbc.queryForObject("select delivered_at from orders where id = ?", Timestamp::class.java, delivered),
            ).isEqualTo(later)
            // 이미 있는 고객(테스트 고객 11 등)은 건드리지 않고, 다시 돌려도 새로 넣지 않는다.
            assertThat(customerMigration.backfillCustomers()).isZero()
            // 동의 전이라 보호된 API 는 403.
            mockMvc.get("/api-public/v1/cart") { with(jwt().jwt { it.subject("77") }) }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `등급 안내는 로그인 없이 볼 수 있다`() {
            mockMvc.get("/api-public/v1/tiers").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(4) }
                jsonPath("$[0].code") { value("WELCOME") }
                jsonPath("$[1].code") { value("SILVER") }
                jsonPath("$[1].minAmount") { value(100_000) }
                jsonPath("$[1].earnRate") { value(2) }
                jsonPath("$[3].code") { value("VIP") }
                jsonPath("$[3].color") { value("#7C3AED") }
                jsonPath("$[0].monthlyCoupons.length()") { value(0) }
            }
        }

        @Test
        fun `내부 탈퇴 API 는 토큰이 있어야 하고 고객을 탈퇴 처리하며 푸시 기기·동의를 지운다`() {
            mockMvc
                .put("/api-public/v1/me/push/devices") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"token":"t-11","platform":"ANDROID"}"""
                }.andExpect { status { isNoContent() } }
            mockMvc
                .put("/api-public/v1/me/push/consent") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":true,"night":true}"""
                }.andExpect { status { isOk() } }

            mockMvc.delete("/api-internal/v1/customers/11").andExpect { status { isForbidden() } }
            mockMvc
                .delete("/api-internal/v1/customers/11") { header("X-Internal-Token", "wrong") }
                .andExpect { status { isForbidden() } }
            // 앱 토큰으로도 못 부른다.
            mockMvc.delete("/api-internal/v1/customers/11") { with(me) }.andExpect { status { isForbidden() } }
            assertThat(
                jdbc.queryForObject("select status from commerce_customers where user_id = '11'", String::class.java),
            ).isEqualTo("ACTIVE")

            mockMvc
                .delete("/api-internal/v1/customers/11") { header("X-Internal-Token", "test-internal-token") }
                .andExpect { status { isNoContent() } }
            assertThat(jdbc.queryForObject("select status from commerce_customers where user_id = '11'", String::class.java))
                .isEqualTo("WITHDRAWN")
            assertThat(
                jdbc.queryForObject("select withdrawn_at from commerce_customers where user_id = '11'", Timestamp::class.java),
            ).isNotNull()
            assertThat(jdbc.queryForObject("select count(*) from push_devices where user_id = '11'", Long::class.java)).isZero()
            assertThat(jdbc.queryForObject("select count(*) from push_consents where user_id = '11'", Long::class.java)).isZero()
            mockMvc.get("/api-public/v1/me/customer") { with(me) }.andExpect { status { isNotFound() } }

            // 다시 불러도, 없는 고객이어도 204.
            mockMvc
                .delete("/api-internal/v1/customers/11") { header("X-Internal-Token", "test-internal-token") }
                .andExpect { status { isNoContent() } }
            mockMvc
                .delete("/api-internal/v1/customers/nobody") { header("X-Internal-Token", "test-internal-token") }
                .andExpect { status { isNoContent() } }

            // 다시 가입하면 가입일은 그대로 두고 ACTIVE 로 돌아온다.
            join("11").andExpect {
                status { isCreated() }
                jsonPath("$.joinedAt") { value("2026-09-01T00:00:00") }
            }
        }

        @Test
        fun `백오피스 고객 목록은 회원 id 앞부분·등급·동의로 거르고 가입일 최신 순이다`() {
            support.joinCustomer("5501", agreed = false, migrated = true)
            support.joinCustomer("5502", tierCode = "GOLD")
            jdbc.update(
                "update commerce_customers set joined_at = ? where user_id = '5502'",
                Timestamp.valueOf(LocalDateTime.of(2026, 9, 20, 0, 0)),
            )
            orders.insert("5502", 40_000, TierClock.utc(2026, 9, 3))

            mockMvc.get("/api-admin/v1/customers?q=55") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.content[0].userId") { value("5502") }
                jsonPath("$.content[0].tier.code") { value("GOLD") }
                jsonPath("$.content[0].tier.name") { value("골드") }
                jsonPath("$.content[0].rollingAmount") { value(40_000) }
                jsonPath("$.content[0].status") { value("ACTIVE") }
                jsonPath("$.content[0].migrated") { value(false) }
                jsonPath("$.content[0].name") { doesNotExist() }
                jsonPath("$.content[1].userId") { value("5501") }
                jsonPath("$.content[1].termsAgreedAt") { doesNotExist() }
            }
            mockMvc.get("/api-admin/v1/customers?q=55&agreed=false") { with(admin) }.andExpect {
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].userId") { value("5501") }
            }
            mockMvc.get("/api-admin/v1/customers?tier=GOLD") { with(admin) }.andExpect {
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].userId") { value("5502") }
            }
            mockMvc.get("/api-admin/v1/customers?agreed=true&size=100") { with(admin) }.andExpect {
                jsonPath("$.totalElements") { value(CatalogTestSupport.TEST_CUSTOMERS.size + 1) }
            }
            mockMvc.get("/api-admin/v1/customers") { with(me) }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `회원 화면 배지 조회는 고객인 사람만 돌려준다`() {
            support.joinCustomer("5501", agreed = false, migrated = true)
            mockMvc.get("/api-admin/v1/customers/lookup?userIds=11,5501,nobody") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].userId") { value("11") }
                jsonPath("$[0].agreed") { value(true) }
                jsonPath("$[0].status") { value("ACTIVE") }
                jsonPath("$[0].tier.code") { value("WELCOME") }
                jsonPath("$[1].userId") { value("5501") }
                jsonPath("$[1].agreed") { value(false) }
            }
            val many = (1..101).joinToString(",") { "x$it" }
            mockMvc.get("/api-admin/v1/customers/lookup?userIds=$many") { with(admin) }.andExpect { status { isBadRequest() } }
            mockMvc.get("/api-admin/v1/customers/nobody") { with(admin) }.andExpect { status { isNotFound() } }
        }
    }
