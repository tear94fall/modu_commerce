package com.example.commerce.api.customer

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.api.support.OrderRows
import com.example.commerce.api.support.TierClock
import com.example.commerce.application.point.PointEarnResult
import com.example.commerce.application.point.PointGateway
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

/** 백오피스 회원 허브 요약(GET /api-admin/v1/customers/{userId}/summary). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TierClock.Config::class)
class CustomerSummaryControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val orders: OrderRows,
        private val clock: TierClock,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        @MockitoBean
        private lateinit var pointGateway: PointGateway

        private val jdbc = JdbcTemplate(dataSource)
        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() {
            support.reseed()
            clock.setKst(2026, 9, 28)
        }

        @BeforeEach
        fun points() {
            whenever(pointGateway.earnAmount(any(), any(), any(), anyOrNull())).thenAnswer {
                PointEarnResult(applied = true, amount = it.getArgument(1))
            }
        }

        private fun postJson(
            url: String,
            body: String,
            user: org.springframework.test.web.servlet.request.RequestPostProcessor = me,
        ) = mockMvc.post(url) {
            with(user)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

        /** 앱으로 주문하고 (주문 id, 줄 id 목록). */
        private fun order(vararg names: String): Pair<Int, List<Int>> {
            val addressId =
                JsonPath.read<Int>(
                    postJson(
                        "/api/v1/addresses",
                        """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구"}""",
                    ).andReturn().response.contentAsString,
                    "$.id",
                )
            val items = names.joinToString(",") { """{"skuId":${support.skuId(it)},"quantity":1}""" }
            val body =
                postJson("/api/v1/orders", """{"addressId":$addressId,"items":[$items]}""")
                    .andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString
            return JsonPath.read<Int>(body, "$.id") to JsonPath.read<List<Int>>(body, "$.items[*].id")
        }

        private fun status(
            id: Int,
            next: String,
        ) = mockMvc
            .patch("/api-admin/v1/orders/$id/status") {
                with(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"$next"}"""
            }.andExpect { status { isOk() } }

        private fun review(itemId: Int): Int =
            JsonPath.read(
                postJson("/api/v1/reviews", """{"orderItemId":$itemId,"rating":5,"content":"좋아요 잘 쓰고 있어요"}""")
                    .andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )

        private fun userCoupon(
            couponId: Int,
            userId: String,
            key: String,
            expiresOn: LocalDate,
            usedAt: LocalDateTime? = null,
        ) {
            val now = Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 0, 0))
            jdbc.update(
                "insert into user_coupons (created_at, updated_at, coupon_id, user_id, source, expires_on, issue_key, used_at) " +
                    "values (?, ?, ?, ?, 'ADMIN', ?, ?, ?)",
                now,
                now,
                couponId,
                userId,
                Date.valueOf(expiresOn),
                key,
                usedAt?.let { Timestamp.valueOf(it) },
            )
        }

        @Test
        fun `주문·쿠폰·찜·리뷰가 섞인 고객의 요약`() {
            // 오래된 주문(상품 줄 없이): 취소 1, 배송중 1, 배송 완료 1(30,000 − 쿠폰 2,000 − 포인트 1,000).
            orders.insert("11", 9_000, null, status = "CANCELLED")
            orders.insert("11", 12_000, null, status = "SHIPPING")
            orders.insert("11", 30_000, TierClock.utc(2026, 8, 10), coupon = 2_000, points = 1_000)
            // 다른 회원 주문은 세지 않는다.
            orders.insert("22", 50_000, TierClock.utc(2026, 8, 10))

            // 다이어리(15,000) + 스티커(5,000) → 배송 완료.
            val (delivered, deliveredItems) = order("모두 다이어리 2027", "모두 스티커 팩")
            status(delivered, "SHIPPING")
            status(delivered, "DELIVERED")
            // 머그컵 → 취소.
            val (cancelled, _) = order("모두 머그컵 세트")
            mockMvc.post("/api/v1/orders/$cancelled/cancel") { with(me) }.andExpect { status { isOk() } }
            // 노트 → 결제 완료.
            val (paid, paidItems) = order("모두 하드커버 노트")

            // 쿠폰: 사용 가능 2(오늘 만료 포함), 사용 1, 만료 1. 다른 회원 1.
            val couponId =
                JsonPath.read<Int>(
                    postJson(
                        "/api-admin/v1/coupons",
                        """{"name":"요약 쿠폰","discountType":"FIXED","discountValue":1000,"minOrderAmount":0,"scope":"ALL",
                           "issueStart":"2026-09-01","issueEnd":"2026-09-30","validDays":7,"totalQuantity":null,
                           "code":null,"downloadable":false,"active":true}""",
                        admin,
                    ).andExpect { status { isCreated() } }
                        .andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            userCoupon(couponId, "11", "a", LocalDate.of(2026, 9, 28))
            userCoupon(couponId, "11", "b", LocalDate.of(2026, 10, 5))
            userCoupon(couponId, "11", "c", LocalDate.of(2026, 9, 1), usedAt = LocalDateTime.of(2026, 8, 30, 3, 0))
            userCoupon(couponId, "11", "d", LocalDate.of(2026, 9, 27))
            userCoupon(couponId, "22", "a", LocalDate.of(2026, 10, 5))
            // 앱 내 쿠폰과 같은 상태여야 한다.
            mapOf("AVAILABLE" to 2, "USED" to 1, "EXPIRED" to 1).forEach { (status, count) ->
                mockMvc.get("/api/v1/me/coupons?status=$status") { with(me) }.andExpect { jsonPath("$.length()") { value(count) } }
            }

            // 찜 3개 중 1개 상품은 삭제된다.
            listOf("모두 볼캡", "모두 디퓨저", "모두 무드등").forEach {
                mockMvc.post("/api/v1/wishlist/${support.productId(it)}") { with(me) }.andExpect { status { is2xxSuccessful() } }
            }
            jdbc.update("update products set deleted_at = ? where name = '모두 무드등'", Timestamp.valueOf(LocalDateTime.now()))

            // 리뷰 3개 중 1개는 지운다.
            review(deliveredItems[0])
            review(deliveredItems[1])
            val removed = review(paidItems[0])
            mockMvc.delete("/api/v1/reviews/$removed") { with(me) }.andExpect { status { is2xxSuccessful() } }

            val body =
                mockMvc
                    .get("/api-admin/v1/customers/11/summary") { with(admin) }
                    .andExpect {
                        status { isOk() }
                        jsonPath("$.customer.userId") { value("11") }
                        jsonPath("$.customer.tier.code") { value("WELCOME") }
                        jsonPath("$.customer.status") { value("ACTIVE") }
                        jsonPath("$.customer.tierHistory") { doesNotExist() }
                        jsonPath("$.orderCounts.PAID") { value(1) }
                        jsonPath("$.orderCounts.SHIPPING") { value(1) }
                        jsonPath("$.orderCounts.DELIVERED") { value(2) }
                        jsonPath("$.orderCounts.CANCELLED") { value(2) }
                        jsonPath("$.deliveredAmountTotal") { value(27_000 + 20_000) }
                        jsonPath("$.lastOrderAt") { exists() }
                        jsonPath("$.recentOrders.length()") { value(5) }
                        jsonPath("$.recentOrders[0].id") { value(paid) }
                        jsonPath("$.recentOrders[0].status") { value("PAID") }
                        jsonPath("$.recentOrders[0].itemSummary") { value("모두 하드커버 노트") }
                        jsonPath("$.recentOrders[0].paymentAmount") { value(12_000) }
                        jsonPath("$.recentOrders[0].orderNo") { exists() }
                        jsonPath("$.recentOrders[0].createdAt") { exists() }
                        jsonPath("$.recentOrders[1].id") { value(cancelled) }
                        jsonPath("$.recentOrders[1].status") { value("CANCELLED") }
                        jsonPath("$.recentOrders[2].id") { value(delivered) }
                        jsonPath("$.recentOrders[2].itemSummary") { value("모두 다이어리 2027 외 1건") }
                        jsonPath("$.recentOrders[2].paymentAmount") { value(20_000) }
                        jsonPath("$.recentOrders[4].status") { value("SHIPPING") }
                        jsonPath("$.coupons.available") { value(2) }
                        jsonPath("$.coupons.used") { value(1) }
                        jsonPath("$.coupons.expired") { value(1) }
                        jsonPath("$.wishlistCount") { value(2) }
                        jsonPath("$.reviewCount") { value(2) }
                        jsonPath("$.points") { value(null) }
                    }.andReturn()
                    .response.contentAsString
            // 마지막 주문 시각은 가장 최근 주문의 생성 시각.
            assertThat(JsonPath.read<String>(body, "$.lastOrderAt")).isEqualTo(JsonPath.read<String>(body, "$.recentOrders[0].createdAt"))
        }

        @Test
        fun `없는 회원이어도 200 이고 0·null 로 채운다`() {
            mockMvc.get("/api-admin/v1/customers/nobody/summary") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.customer") { value(null) }
                jsonPath("$.orderCounts.PAID") { value(0) }
                jsonPath("$.orderCounts.SHIPPING") { value(0) }
                jsonPath("$.orderCounts.DELIVERED") { value(0) }
                jsonPath("$.orderCounts.CANCELLED") { value(0) }
                jsonPath("$.deliveredAmountTotal") { value(0) }
                jsonPath("$.lastOrderAt") { value(null) }
                jsonPath("$.recentOrders.length()") { value(0) }
                jsonPath("$.coupons.available") { value(0) }
                jsonPath("$.coupons.used") { value(0) }
                jsonPath("$.coupons.expired") { value(0) }
                jsonPath("$.wishlistCount") { value(0) }
                jsonPath("$.reviewCount") { value(0) }
                jsonPath("$.points") { value(null) }
            }
        }

        @Test
        fun `관리자가 아니면 막힌다`() {
            mockMvc.get("/api-admin/v1/customers/11/summary").andExpect { status { isUnauthorized() } }
            mockMvc.get("/api-admin/v1/customers/11/summary") { with(me) }.andExpect { status { isForbidden() } }
        }
    }
