package com.example.commerce.api.promotion

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.point.PointEarnResult
import com.example.commerce.application.point.PointGateway
import com.example.commerce.application.point.PointGatewayException
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@SpringBootTest
@AutoConfigureMockMvc
class PromotionControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val clock: MovableClock,
    ) {
        @MockitoBean
        private lateinit var pointGateway: PointGateway

        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() {
            support.reseed()
            clock.today = LocalDate.of(2026, 9, 25)
        }

        @BeforeEach
        fun points() {
            whenever(pointGateway.earn(any(), any(), any(), any())).thenReturn(PointEarnResult(applied = true, amount = 10))
        }

        private fun create(body: String): Int =
            JsonPath.read(
                mockMvc
                    .post("/api-admin/v1/promotions") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = body
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )

        private fun exhibition(
            title: String = "가을 기획전",
            start: String = "2026-09-20",
            end: String = "2026-09-30",
            visible: Boolean = true,
            sortOrder: Int = 0,
        ): Int {
            val ids = listOf("모두 스티커 팩", "모두 다이어리 2027").map { support.productId(it) }
            return create(
                """{"type":"EXHIBITION","title":"$title","subtitle":"가을맞이","startDate":"$start","endDate":"$end",
                   "visible":$visible,"sortOrder":$sortOrder,"bannerColor":"#E11D48","productIds":[${ids[1]},${ids[0]}]}""",
            )
        }

        private fun event(rule: String? = "EVENT_ATTEND"): Int =
            create(
                """{"type":"EVENT","title":"출석 체크","startDate":"2026-09-24","endDate":"2026-09-26","visible":true,
                   "pointRuleCode":${rule?.let { "\"$it\"" } ?: "null"},"rewardPoints":${if (rule == null) "null" else "10"}}""",
            )

        @Test
        fun `banners show only visible promotions running today, in admin order`() {
            val later = exhibition(title = "두 번째", sortOrder = 2)
            val first = exhibition(title = "첫 번째", sortOrder = 1)
            exhibition(title = "숨김", visible = false)
            exhibition(title = "다음 달", start = "2026-10-01", end = "2026-10-31")
            exhibition(title = "지난 달", start = "2026-08-01", end = "2026-08-31")

            mockMvc.get("/api/v1/promotions/banners") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].id") { value(first) }
                jsonPath("$[1].id") { value(later) }
                jsonPath("$[0].bannerColor") { value("#E11D48") }
                jsonPath("$[0].startDate") { value("2026-09-20") }
            }
        }

        @Test
        fun `exhibition detail lists products in admin order, hidden promotions are 404`() {
            val id = exhibition()
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.type") { value("EXHIBITION") }
                jsonPath("$.status") { value("ONGOING") }
                jsonPath("$.products.length()") { value(2) }
                jsonPath("$.products[0].name") { value("모두 다이어리 2027") }
                jsonPath("$.products[1].name") { value("모두 스티커 팩") }
                jsonPath("$.attendance") { doesNotExist() }
            }
            val hidden = exhibition(visible = false)
            mockMvc.get("/api/v1/promotions/$hidden") { with(me) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `attendance credits the rule once per day and shows the calendar`() {
            val id = event()
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect {
                jsonPath("$.attendance.rewardPoints") { value(10) }
                jsonPath("$.attendance.today") { value("2026-09-25") }
                jsonPath("$.attendance.checkedToday") { value(false) }
                jsonPath("$.attendance.totalDays") { value(3) }
            }

            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.checkedDate") { value("2026-09-25") }
                jsonPath("$.rewardPoints") { value(10) }
                jsonPath("$.checkedDates[0]") { value("2026-09-25") }
            }
            verify(pointGateway).earn(eq("11"), eq("EVENT_ATTEND"), eq("attend:$id:2026-09-25"), any())

            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect {
                status { isConflict() }
                jsonPath("$.message") { value("오늘은 이미 출석했습니다.") }
            }

            clock.today = LocalDate.of(2026, 9, 26)
            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.checkedDates.length()") { value(2) }
            }
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect {
                jsonPath("$.attendance.checkedToday") { value(true) }
                jsonPath("$.attendance.checkedDates.length()") { value(2) }
            }

            clock.today = LocalDate.of(2026, 9, 27)
            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("진행 중인 이벤트가 아닙니다.") }
            }

            mockMvc.get("/api-admin/v1/promotions/$id/attendances") { with(admin) }.andExpect {
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.content[0].checkDate") { value("2026-09-26") }
                jsonPath("$.content[0].rewardPoints") { value(10) }
            }
        }

        @Test
        fun `a limit keeps the attendance with 0 points, an outage keeps nothing`() {
            val id = event()
            whenever(pointGateway.earn(any(), any(), any(), any())).thenThrow(PointGatewayException())
            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect { status { isServiceUnavailable() } }
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect { jsonPath("$.attendance.checkedToday") { value(false) } }

            whenever(
                pointGateway.earn(any(), any(), any(), any()),
            ).thenReturn(PointEarnResult(applied = false, amount = 0, reason = "DAILY_LIMIT"))
            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.rewardPoints") { value(0) }
                jsonPath("$.rewardMessage") { value("오늘 받을 수 있는 포인트를 이미 받아 적립되지 않았습니다.") }
            }
        }

        @Test
        fun `an event without a reward rule records attendance without calling the point service`() {
            val id = event(rule = null)
            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.rewardPoints") { value(0) }
                jsonPath("$.rewardMessage") { doesNotExist() }
            }
            verify(pointGateway, never()).earn(any(), any(), any(), any())
        }

        @Test
        fun `exhibitions cannot be checked in`() {
            val id = exhibition()
            mockMvc.post("/api/v1/promotions/$id/attendance") { with(me) }.andExpect { status { isBadRequest() } }
        }

        @Test
        fun `admin list, detail, update, validation and delete`() {
            val ex = exhibition()
            val ev = event()
            mockMvc.get("/api-admin/v1/promotions?type=EVENT") { with(admin) }.andExpect {
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].id") { value(ev) }
                jsonPath("$.content[0].status") { value("ONGOING") }
            }
            mockMvc.get("/api-admin/v1/promotions?q=가을") { with(admin) }.andExpect { jsonPath("$.content[0].productCount") { value(2) } }
            mockMvc.get("/api-admin/v1/promotions/$ex") { with(admin) }.andExpect {
                jsonPath("$.products[0].name") { value("모두 다이어리 2027") }
                jsonPath("$.products[0].status") { value("SELLING") }
                jsonPath("$.subtitle") { value("가을맞이") }
            }
            mockMvc.get("/api-admin/v1/promotions/$ev") { with(admin) }.andExpect {
                jsonPath("$.pointRuleCode") { value("EVENT_ATTEND") }
                jsonPath("$.rewardPoints") { value(10) }
            }

            val sticker = support.productId("모두 스티커 팩")
            mockMvc
                .put("/api-admin/v1/promotions/$ex") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"type":"EXHIBITION","title":"이름 바꿈","startDate":"2026-09-20","endDate":"2026-09-30","visible":true,"productIds":[$sticker]}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.title") { value("이름 바꿈") }
                    jsonPath("$.products.length()") { value(1) }
                    jsonPath("$.subtitle") { doesNotExist() }
                }

            fun bad(
                url: String,
                body: String,
                message: String,
                method: String = "POST",
            ) {
                val action =
                    if (method == "POST") {
                        mockMvc.post(url) {
                            with(admin)
                            contentType = MediaType.APPLICATION_JSON
                            content = body
                        }
                    } else {
                        mockMvc.put(url) {
                            with(admin)
                            contentType = MediaType.APPLICATION_JSON
                            content = body
                        }
                    }
                action.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value(message) }
                }
            }
            bad(
                "/api-admin/v1/promotions",
                """{"type":"EVENT","title":"x","startDate":"2026-09-26","endDate":"2026-09-25","visible":true}""",
                "종료일은 시작일보다 빠를 수 없습니다.",
            )
            bad(
                "/api-admin/v1/promotions",
                """{"type":"EXHIBITION","title":"x","startDate":"2026-09-25","endDate":"2026-09-25","visible":true,"productIds":[]}""",
                "기획전 상품을 1~100개 고르세요.",
            )
            bad(
                "/api-admin/v1/promotions",
                """{"type":"EXHIBITION","title":"x","startDate":"2026-09-25","endDate":"2026-09-25","visible":true,"productIds":[999999]}""",
                "없는 상품이 있습니다: 999999",
            )
            bad(
                "/api-admin/v1/promotions",
                """{"type":"EVENT","title":"x","bannerColor":"red","startDate":"2026-09-25","endDate":"2026-09-25","visible":true}""",
                "배너 색은 #RRGGBB 형식으로 입력하세요.",
            )
            bad(
                "/api-admin/v1/promotions/$ev",
                """{"type":"EXHIBITION","title":"x","startDate":"2026-09-25","endDate":"2026-09-25","visible":true,"productIds":[$sticker]}""",
                "기획전·이벤트 종류는 바꿀 수 없습니다.",
                "PUT",
            )

            mockMvc.get("/api-admin/v1/promotions") { with(me) }.andExpect { status { isForbidden() } }

            mockMvc.delete("/api-admin/v1/promotions/$ex") { with(admin) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api/v1/promotions/$ex") { with(me) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api-admin/v1/promotions") { with(admin) }.andExpect { jsonPath("$.totalElements") { value(1) } }
        }

        /** 오늘(KST)을 테스트가 정한다. */
        class MovableClock : Clock() {
            var today: LocalDate = LocalDate.of(2026, 9, 25)

            override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = today.atTime(12, 0).toInstant(ZoneOffset.ofHours(9))
        }

        @TestConfiguration
        class ClockTestConfig {
            @Bean
            @Primary
            fun movableClock(): MovableClock = MovableClock()
        }
    }
