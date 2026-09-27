package com.example.commerce.api.push

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.domain.repository.rw.PushDeviceRwRepository
import com.example.commerce.application.push.PushMessage
import com.example.commerce.application.push.PushSendResult
import com.example.commerce.application.push.PushSender
import com.example.commerce.application.service.PushCampaignSendService
import com.example.commerce.application.service.PushInboxCommandService
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
class PushCampaignControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val clock: PushClock,
        private val sender: RecordingPushSender,
        private val sendService: PushCampaignSendService,
        private val deviceRepository: PushDeviceRwRepository,
        private val inboxCommandService: PushInboxCommandService,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        private val jdbc = JdbcTemplate(dataSource)
        private val admin = jwt().jwt { it.subject("staff-7") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        private fun user(id: String) = jwt().jwt { it.subject(id) }

        @BeforeEach
        @AfterEach
        fun reset() {
            support.reseed()
            clock.set(2026, 9, 27, 12, 0)
            sender.reset()
        }

        private fun registerDevice(
            userId: String,
            token: String,
        ) {
            mockMvc
                .put("/api/v1/me/push/devices") {
                    with(user(userId))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"token":"$token","platform":"ANDROID"}"""
                }.andExpect { status { isNoContent() } }
        }

        private fun consent(
            userId: String,
            marketing: Boolean,
            night: Boolean = false,
        ) {
            mockMvc
                .put("/api/v1/me/push/consent") {
                    with(user(userId))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":$marketing,"night":$night}"""
                }.andExpect { status { isOk() } }
        }

        private fun create(
            body: String,
            expectCreated: Boolean = true,
        ): Int? {
            val result =
                mockMvc
                    .post("/api-admin/v1/push-campaigns") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = body
                    }.andReturn()
                    .response
            if (!expectCreated) return result.status
            assertThat(result.status).describedAs(result.contentAsString).isEqualTo(201)
            return JsonPath.read(result.contentAsString, "$.id")
        }

        private fun productCampaign(scheduledAt: String? = "2026-09-27T12:30:00+09:00"): Int {
            val productId = support.productId("모두 스티커 팩")
            return create(
                """{"title":"특가 아이템 보러가기","body":"오늘만 반값","targetType":"PRODUCT","targetId":$productId,
                   "scheduledAt":${scheduledAt?.let { "\"$it\"" } ?: "null"}}""",
            )!!
        }

        @Test
        fun `device register moves a token to the new owner and delete only removes my own`() {
            registerDevice("u1", "tok-a")
            assertThat(deviceRepository.findByToken("tok-a")!!.userId).isEqualTo("u1")

            registerDevice("u2", "tok-a")
            assertThat(deviceRepository.findAll().filter { it.token == "tok-a" }).hasSize(1)
            assertThat(deviceRepository.findByToken("tok-a")!!.userId).isEqualTo("u2")

            mockMvc.delete("/api/v1/me/push/devices?token=tok-a") { with(user("u1")) }.andExpect { status { isNoContent() } }
            assertThat(deviceRepository.findByToken("tok-a")).isNotNull

            mockMvc.delete("/api/v1/me/push/devices?token=tok-a") { with(user("u2")) }.andExpect { status { isNoContent() } }
            assertThat(deviceRepository.findByToken("tok-a")).isNull()

            mockMvc
                .put("/api/v1/me/push/devices") {
                    with(user("u1"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"token":"  "}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { exists() }
                }
        }

        @Test
        fun `consent defaults off, night is forced off without marketing, timestamps change only for changed flags`() {
            mockMvc.get("/api/v1/me/push/consent") { with(user("u1")) }.andExpect {
                status { isOk() }
                jsonPath("$.marketing") { value(false) }
                jsonPath("$.marketingUpdatedAt") { value(nullValue()) }
                jsonPath("$.night") { value(false) }
                jsonPath("$.nightUpdatedAt") { value(nullValue()) }
            }

            // 밤 동의만 켜려 해도 혜택 알림이 꺼져 있으면 꺼진 채(바뀐 것 없음)
            mockMvc
                .put("/api/v1/me/push/consent") {
                    with(user("u1"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":false,"night":true}"""
                }.andExpect {
                    jsonPath("$.night") { value(false) }
                    jsonPath("$.nightUpdatedAt") { value(nullValue()) }
                }

            // 12:00 KST = 03:00 UTC
            mockMvc
                .put("/api/v1/me/push/consent") {
                    with(user("u1"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":true,"night":true}"""
                }.andExpect {
                    jsonPath("$.marketing") { value(true) }
                    jsonPath("$.marketingUpdatedAt") { value(startsWith("2026-09-27T03:00")) }
                    jsonPath("$.night") { value(true) }
                    jsonPath("$.nightUpdatedAt") { value(startsWith("2026-09-27T03:00")) }
                }

            clock.set(2026, 9, 27, 15, 0)
            mockMvc
                .put("/api/v1/me/push/consent") {
                    with(user("u1"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":true,"night":false}"""
                }.andExpect {
                    jsonPath("$.marketingUpdatedAt") { value(startsWith("2026-09-27T03:00")) }
                    jsonPath("$.night") { value(false) }
                    jsonPath("$.nightUpdatedAt") { value(startsWith("2026-09-27T06:00")) }
                }

            clock.set(2026, 9, 27, 16, 0)
            mockMvc
                .put("/api/v1/me/push/consent") {
                    with(user("u1"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":false,"night":true}"""
                }.andExpect {
                    jsonPath("$.marketing") { value(false) }
                    jsonPath("$.marketingUpdatedAt") { value(startsWith("2026-09-27T07:00")) }
                    jsonPath("$.night") { value(false) }
                    jsonPath("$.nightUpdatedAt") { value(startsWith("2026-09-27T06:00")) }
                }

            mockMvc.get("/api/v1/me/push/consent") { with(user("u1")) }.andExpect {
                jsonPath("$.marketingUpdatedAt") { value(startsWith("2026-09-27T07:00")) }
            }
            mockMvc
                .put("/api/v1/me/push/consent") {
                    with(user("u1"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"marketing":true}"""
                }.andExpect { status { isBadRequest() } }
        }

        @Test
        fun `campaign create validates content, schedule window and target`() {
            val productId = support.productId("모두 스티커 팩")

            fun body(
                title: String = "제목",
                text: String = "내용",
                target: String = """"targetType":"PRODUCT","targetId":$productId""",
                scheduledAt: String = "null",
                imageUrl: String = "null",
            ) = """{"title":"$title","body":"$text",$target,"scheduledAt":$scheduledAt,"imageUrl":$imageUrl}"""

            assertThat(create(body(title = "가".repeat(41)), expectCreated = false)).isEqualTo(400)
            assertThat(create(body(title = "  "), expectCreated = false)).isEqualTo(400)
            assertThat(create(body(text = "가".repeat(121)), expectCreated = false)).isEqualTo(400)
            assertThat(create(body(imageUrl = "\"ftp://x/a.png\""), expectCreated = false)).isEqualTo(400)
            // 1분 넘게 지난 예약, 30일 넘는 예약
            assertThat(create(body(scheduledAt = "\"2026-09-27T11:58:00+09:00\""), expectCreated = false)).isEqualTo(400)
            assertThat(create(body(scheduledAt = "\"2026-10-28T12:00:00+09:00\""), expectCreated = false)).isEqualTo(400)
            // 대상 없음 / 없는 상품 / 숨긴 상품 / 숨긴 기획전
            assertThat(create(body(target = """"targetType":"PRODUCT""""), expectCreated = false)).isEqualTo(400)
            assertThat(create(body(target = """"targetId":$productId"""), expectCreated = false)).isEqualTo(400)
            assertThat(create(body(target = """"targetType":"PRODUCT","targetId":999999"""), expectCreated = false)).isEqualTo(400)
            assertThat(create(body(target = """"targetType":"PROMOTION","targetId":999999"""), expectCreated = false)).isEqualTo(400)
            jdbc.update("update products set status = 'HIDDEN' where id = ?", productId)
            assertThat(create(body(), expectCreated = false)).isEqualTo(400)

            val hiddenPromotion =
                JsonPath.read<Int>(
                    mockMvc
                        .post("/api-admin/v1/promotions") {
                            with(admin)
                            contentType = MediaType.APPLICATION_JSON
                            content =
                                """{"type":"EVENT","title":"숨김 이벤트","startDate":"2026-09-20","endDate":"2026-09-30","visible":false}"""
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            assertThat(create(body(target = """"targetType":"PROMOTION","targetId":$hiddenPromotion"""), expectCreated = false))
                .isEqualTo(400)

            // 1분 안쪽으로 지난 시각, 딱 30일 뒤는 된다. 쿠폰함·홈은 대상 id 가 필요 없다(와도 버린다).
            create(body(target = """"targetType":"COUPONS","targetId":5""", scheduledAt = "\"2026-09-27T11:59:30+09:00\""))
            val home = create(body(target = """"targetType":"HOME"""", scheduledAt = "\"2026-10-27T12:00:00+09:00\""))
            mockMvc.get("/api-admin/v1/push-campaigns/$home") { with(admin) }.andExpect {
                jsonPath("$.targetType") { value("HOME") }
                jsonPath("$.targetId") { value(nullValue()) }
                jsonPath("$.targetLabel") { value(nullValue()) }
                jsonPath("$.path") { value("/") }
                jsonPath("$.status") { value("SCHEDULED") }
                jsonPath("$.scheduledAt") { value(startsWith("2026-10-27T03:00")) }
                jsonPath("$.createdBy") { value("staff-7") }
            }
            mockMvc.get("/api-admin/v1/push-campaigns/999999") { with(admin) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `scheduler sends a due campaign only to marketing-consented devices with the ad prefix and suffix`() {
            registerDevice("u1", "tok-1")
            registerDevice("u1", "tok-1b")
            registerDevice("u2", "tok-2")
            registerDevice("u3", "tok-3")
            consent("u1", marketing = true)
            consent("u3", marketing = false)
            val id = productCampaign()

            assertThat(sendService.runDue()).isEqualTo(0)
            assertThat(sender.calls).isEmpty()

            clock.set(2026, 9, 27, 12, 30)
            assertThat(sendService.runDue()).isEqualTo(1)
            assertThat(sender.calls).hasSize(1)
            val (tokens, message) = sender.calls.single()
            assertThat(tokens).containsExactlyInAnyOrder("tok-1", "tok-1b")
            assertThat(message.title).isEqualTo("(광고) 특가 아이템 보러가기")
            assertThat(message.body).isEqualTo("오늘만 반값\n수신거부: 마이페이지 > 알림 설정")
            val productId = support.productId("모두 스티커 팩")
            assertThat(message.data).isEqualTo(mapOf("type" to "campaign", "campaignId" to "$id", "path" to "/products/$productId"))

            mockMvc.get("/api-admin/v1/push-campaigns/$id") { with(admin) }.andExpect {
                jsonPath("$.status") { value("SENT") }
                jsonPath("$.targetLabel") { value("모두 스티커 팩") }
                jsonPath("$.path") { value("/products/$productId") }
                jsonPath("$.nightApplied") { value(false) }
                jsonPath("$.targetUsers") { value(1) }
                jsonPath("$.targetDevices") { value(2) }
                jsonPath("$.successCount") { value(2) }
                jsonPath("$.failureCount") { value(0) }
                jsonPath("$.sentAt") { value(startsWith("2026-09-27T03:30")) }
            }
            // 이미 보낸 캠페인은 다시 나가지 않는다
            assertThat(sendService.runDue()).isEqualTo(0)
        }

        @Test
        fun `send now at night reaches only night-consented users`() {
            registerDevice("u1", "tok-1")
            registerDevice("u3", "tok-3")
            consent("u1", marketing = true)
            consent("u3", marketing = true, night = true)
            clock.set(2026, 9, 27, 22, 0)

            val id = productCampaign(scheduledAt = null)

            assertThat(sender.calls.single().first).containsExactly("tok-3")
            mockMvc.get("/api-admin/v1/push-campaigns/$id") { with(admin) }.andExpect {
                jsonPath("$.status") { value("SENT") }
                jsonPath("$.nightApplied") { value(true) }
                jsonPath("$.targetUsers") { value(1) }
                jsonPath("$.successCount") { value(1) }
            }
        }

        @Test
        fun `claim lets only one sender send a campaign`() {
            registerDevice("u1", "tok-1")
            consent("u1", marketing = true)
            val id = productCampaign().toLong()
            clock.set(2026, 9, 27, 12, 30)

            assertThat(sendService.send(id)).isTrue()
            assertThat(sendService.send(id)).isFalse()
            assertThat(sendService.runDue()).isEqualTo(0)
            assertThat(sender.calls).hasSize(1)
        }

        @Test
        fun `invalid tokens are deleted and counted, a sender error marks the campaign FAILED`() {
            registerDevice("u1", "tok-1")
            registerDevice("u3", "tok-3")
            consent("u1", marketing = true)
            consent("u3", marketing = true)
            sender.invalid = setOf("tok-1")

            val id = productCampaign(scheduledAt = null)
            mockMvc.get("/api-admin/v1/push-campaigns/$id") { with(admin) }.andExpect {
                jsonPath("$.status") { value("SENT") }
                jsonPath("$.targetDevices") { value(2) }
                jsonPath("$.successCount") { value(1) }
                jsonPath("$.failureCount") { value(1) }
                jsonPath("$.removedTokens") { value(1) }
            }
            assertThat(deviceRepository.findByToken("tok-1")).isNull()
            assertThat(deviceRepository.findByToken("tok-3")).isNotNull

            sender.error = IllegalStateException("FCM 인증 실패")
            val failed = productCampaign(scheduledAt = null)
            mockMvc.get("/api-admin/v1/push-campaigns/$failed") { with(admin) }.andExpect {
                jsonPath("$.status") { value("FAILED") }
                jsonPath("$.failureMessage") { value("FCM 인증 실패") }
                jsonPath("$.targetDevices") { value(1) }
            }
        }

        @Test
        fun `cancel stops a scheduled campaign and is 409 once it is not scheduled`() {
            registerDevice("u1", "tok-1")
            consent("u1", marketing = true)
            val id = productCampaign()

            mockMvc.post("/api-admin/v1/push-campaigns/$id/cancel") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELED") }
                jsonPath("$.canceledAt") { value(startsWith("2026-09-27T03:00")) }
            }
            mockMvc.post("/api-admin/v1/push-campaigns/$id/cancel") { with(admin) }.andExpect {
                status { isConflict() }
                jsonPath("$.message") { exists() }
            }
            clock.set(2026, 9, 27, 13, 0)
            assertThat(sendService.runDue()).isEqualTo(0)
            assertThat(sender.calls).isEmpty()

            val sent = productCampaign(scheduledAt = null)
            mockMvc.post("/api-admin/v1/push-campaigns/$sent/cancel") { with(admin) }.andExpect { status { isConflict() } }
            mockMvc.post("/api-admin/v1/push-campaigns/999999/cancel") { with(admin) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `audience counts who would receive at the given moment`() {
            registerDevice("u1", "tok-1")
            registerDevice("u1", "tok-1b")
            registerDevice("u2", "tok-2")
            registerDevice("u3", "tok-3")
            consent("u1", marketing = true)
            consent("u3", marketing = true, night = true)
            consent("u4", marketing = true, night = true) // 기기 없음

            mockMvc.get("/api-admin/v1/push-campaigns/audience") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.night") { value(false) }
                jsonPath("$.users") { value(2) }
                jsonPath("$.devices") { value(3) }
                jsonPath("$.consentedUsers") { value(3) }
                jsonPath("$.nightUsers") { value(2) }
            }
            mockMvc
                .get("/api-admin/v1/push-campaigns/audience") {
                    with(admin)
                    param("at", "2026-09-27T23:00:00+09:00")
                }.andExpect {
                    jsonPath("$.night") { value(true) }
                    jsonPath("$.users") { value(1) }
                    jsonPath("$.devices") { value(1) }
                }
            // 인코딩 안 한 + 는 공백으로 온다
            mockMvc
                .get("/api-admin/v1/push-campaigns/audience") {
                    with(admin)
                    param("at", "2026-09-28T07:59:00 09:00")
                }.andExpect {
                    jsonPath("$.night") { value(true) }
                }
            mockMvc
                .get("/api-admin/v1/push-campaigns/audience") {
                    with(admin)
                    param("at", "2026-09-27T23:00:00Z")
                }.andExpect {
                    jsonPath("$.night") { value(false) }
                }
            mockMvc.get("/api-admin/v1/push-campaigns/audience?at=tomorrow") { with(admin) }.andExpect { status { isBadRequest() } }
        }

        @Test
        fun `test send ignores consent, marks the title and is not stored`() {
            registerDevice("u2", "tok-2")
            mockMvc
                .post("/api-admin/v1/push-campaigns/test") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"특가","body":"보러가기","targetType":"COUPONS","userIds":["u2","u9"]}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.users") { value(1) }
                    jsonPath("$.devices") { value(1) }
                    jsonPath("$.success") { value(1) }
                    jsonPath("$.failure") { value(0) }
                    jsonPath("$.noDeviceUserIds[0]") { value("u9") }
                }
            val (tokens, message) = sender.calls.single()
            assertThat(tokens).containsExactly("tok-2")
            assertThat(message.title).isEqualTo("[테스트] (광고) 특가")
            assertThat(message.body).isEqualTo("보러가기\n수신거부: 마이페이지 > 알림 설정")
            assertThat(message.data).isEqualTo(mapOf("type" to "campaign", "path" to "/coupons"))

            mockMvc.get("/api-admin/v1/push-campaigns") { with(admin) }.andExpect { jsonPath("$.totalElements") { value(0) } }

            mockMvc
                .post("/api-admin/v1/push-campaigns/test") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"특가","body":"보러가기","targetType":"HOME","userIds":[]}"""
                }.andExpect { status { isBadRequest() } }
            mockMvc
                .post("/api-admin/v1/push-campaigns/test") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"특가","body":"보러가기","targetType":"HOME","userIds":["a","b","c","d","e","f"]}"""
                }.andExpect { status { isBadRequest() } }
        }

        @Test
        fun `opened is counted once per user and unknown campaigns are ignored`() {
            val id = productCampaign(scheduledAt = null)
            repeat(2) { mockMvc.post("/api/v1/push/campaigns/$id/opened") { with(user("u1")) }.andExpect { status { isNoContent() } } }
            mockMvc.post("/api/v1/push/campaigns/$id/opened") { with(user("u3")) }.andExpect { status { isNoContent() } }
            mockMvc.post("/api/v1/push/campaigns/999999/opened") { with(user("u1")) }.andExpect { status { isNoContent() } }

            mockMvc.get("/api-admin/v1/push-campaigns/$id") { with(admin) }.andExpect { jsonPath("$.openedCount") { value(2) } }
        }

        @Test
        fun `list filters by status and title, newest send time first`() {
            val early = productCampaign("2026-09-27T13:00:00+09:00")
            val late = productCampaign("2026-09-28T13:00:00+09:00")
            val other =
                create(
                    """{"title":"쿠폰 도착","body":"쿠폰함을 확인하세요","targetType":"COUPONS","scheduledAt":"2026-09-27T18:00:00+09:00"}""",
                )!!
            mockMvc.post("/api-admin/v1/push-campaigns/$early/cancel") { with(admin) }

            mockMvc.get("/api-admin/v1/push-campaigns") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(3) }
                jsonPath("$.size") { value(15) }
                jsonPath("$.content[0].id") { value(late) }
                jsonPath("$.content[1].id") { value(other) }
                jsonPath("$.content[2].id") { value(early) }
            }
            mockMvc.get("/api-admin/v1/push-campaigns?status=CANCELED") { with(admin) }.andExpect {
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].id") { value(early) }
            }
            mockMvc
                .get("/api-admin/v1/push-campaigns") {
                    with(admin)
                    param("q", "쿠폰")
                }.andExpect {
                    jsonPath("$.totalElements") { value(1) }
                    jsonPath("$.content[0].title") { value("쿠폰 도착") }
                }
        }

        @Test
        fun `admin endpoints need an admin token`() {
            mockMvc.get("/api-admin/v1/push-campaigns").andExpect { status { isUnauthorized() } }
            mockMvc
                .get("/api-admin/v1/push-campaigns") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }
                .andExpect { status { isForbidden() } }
            mockMvc.put("/api/v1/me/push/consent").andExpect { status { isUnauthorized() } }
        }

        private fun inboxUsers(campaignId: Int): List<String> =
            jdbc.queryForList("select user_id from push_inbox_items where campaign_id = ? order by user_id", String::class.java, campaignId)

        private fun unread(userId: String): Int =
            JsonPath.read(
                mockMvc
                    .get("/api/v1/me/notifications/unread-count") { with(user(userId)) }
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString,
                "$.unread",
            )

        private fun inboxIds(userId: String): List<Int> =
            JsonPath.read(
                mockMvc
                    .get("/api/v1/me/notifications") { with(user(userId)) }
                    .andReturn()
                    .response.contentAsString,
                "$.content[*].id",
            )

        @Test
        fun `inbox rows are written only for users with a delivered device, once per user, never for a test send`() {
            registerDevice("u1", "tok-1")
            registerDevice("u1", "tok-1b")
            registerDevice("u1", "tok-1c")
            registerDevice("u2", "tok-2")
            registerDevice("u3", "tok-3")
            consent("u1", marketing = true)
            consent("u2", marketing = true)
            consent("u4", marketing = true) // 기기 없음
            // u1 은 기기 하나가 무효여도 나머지로 받았다. u2 는 유일한 기기가 무효. u3 는 동의 안 함.
            sender.invalid = setOf("tok-1b", "tok-2")

            val id = productCampaign(scheduledAt = null)

            assertThat(inboxUsers(id)).containsExactly("u1")
            assertThat(jdbc.queryForObject("select created_at from push_inbox_items", LocalDateTime::class.java))
                .isEqualTo(LocalDateTime.of(2026, 9, 27, 3, 0))

            // 이미 있는 줄(같은 사람·같은 캠페인)은 건너뛰고 새 사람만 넣는다
            assertThat(inboxCommandService.add(id.toLong(), listOf("u1", "u5", "u5"))).isEqualTo(1)
            assertThat(inboxUsers(id)).containsExactly("u1", "u5")

            // 테스트 보내기는 알림함에 남기지 않는다
            mockMvc
                .post("/api-admin/v1/push-campaigns/test") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"특가","body":"보러가기","targetType":"COUPONS","userIds":["u1","u3"]}"""
                }.andExpect { status { isOk() } }
            assertThat(jdbc.queryForObject("select count(*) from push_inbox_items", Int::class.java)).isEqualTo(2)

            support.reseed()
            assertThat(jdbc.queryForObject("select count(*) from push_inbox_items", Int::class.java)).isEqualTo(0)
        }

        @Test
        fun `inbox lists raw title and body newest first and only for 30 days`() {
            registerDevice("u1", "tok-1")
            consent("u1", marketing = true)
            val productId = support.productId("모두 스티커 팩")
            val older = productCampaign(scheduledAt = null)
            clock.set(2026, 9, 28, 12, 0)
            val newer =
                create(
                    """{"title":"쿠폰 도착","body":"쿠폰함을 확인하세요","targetType":"COUPONS",
                       "imageUrl":"https://img.test/c.png","scheduledAt":null}""",
                )!!

            mockMvc.get("/api/v1/me/notifications") { with(user("u1")) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.totalPages") { value(1) }
                jsonPath("$.number") { value(0) }
                jsonPath("$.size") { value(20) }
                jsonPath("$.content[0].campaignId") { value(newer) }
                jsonPath("$.content[0].title") { value("쿠폰 도착") }
                jsonPath("$.content[0].body") { value("쿠폰함을 확인하세요") }
                jsonPath("$.content[0].imageUrl") { value("https://img.test/c.png") }
                jsonPath("$.content[0].path") { value("/coupons") }
                jsonPath("$.content[0].receivedAt") { value(startsWith("2026-09-28T03:00")) }
                jsonPath("$.content[0].read") { value(false) }
                jsonPath("$.content[1].campaignId") { value(older) }
                jsonPath("$.content[1].title") { value("특가 아이템 보러가기") }
                jsonPath("$.content[1].body") { value("오늘만 반값") }
                jsonPath("$.content[1].imageUrl") { value(nullValue()) }
                jsonPath("$.content[1].path") { value("/products/$productId") }
                jsonPath("$.content[1].receivedAt") { value(startsWith("2026-09-27T03:00")) }
            }
            // 다른 사람의 알림함은 비어 있다
            mockMvc.get("/api/v1/me/notifications") { with(user("u2")) }.andExpect { jsonPath("$.totalElements") { value(0) } }
            // 한 쪽에 50개까지
            mockMvc
                .get("/api/v1/me/notifications") {
                    with(user("u1"))
                    param("size", "100")
                    param("page", "0")
                }.andExpect { jsonPath("$.size") { value(50) } }
            assertThat(unread("u1")).isEqualTo(2)

            // 받은 지 30일이 지나면 목록·안 읽은 수에서 빠진다
            clock.set(2026, 10, 27, 12, 0)
            assertThat(inboxIds("u1")).hasSize(2)
            clock.set(2026, 10, 27, 12, 1)
            mockMvc.get("/api/v1/me/notifications") { with(user("u1")) }.andExpect {
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].campaignId") { value(newer) }
            }
            assertThat(unread("u1")).isEqualTo(1)
        }

        @Test
        fun `read marks only my own item, read-all and opened mark read too`() {
            registerDevice("u1", "tok-1")
            registerDevice("u2", "tok-2")
            consent("u1", marketing = true)
            consent("u2", marketing = true)
            val first = productCampaign(scheduledAt = null)
            clock.set(2026, 9, 27, 13, 0)
            val second = productCampaign(scheduledAt = null)
            val third = productCampaign(scheduledAt = null)
            assertThat(unread("u1")).isEqualTo(3)
            val (u1Third, u1Second, _) = inboxIds("u1")
            val u2Items = inboxIds("u2")

            // 남의 줄·없는 줄은 404
            mockMvc.post("/api/v1/me/notifications/${u2Items[0]}/read") { with(user("u1")) }.andExpect {
                status { isNotFound() }
                jsonPath("$.message") { exists() }
            }
            mockMvc.post("/api/v1/me/notifications/999999/read") { with(user("u1")) }.andExpect { status { isNotFound() } }
            assertThat(unread("u2")).isEqualTo(3)

            // 내 줄은 읽음(두 번 해도 204)
            repeat(2) {
                mockMvc.post("/api/v1/me/notifications/$u1Third/read") { with(user("u1")) }.andExpect { status { isNoContent() } }
            }
            assertThat(unread("u1")).isEqualTo(2)
            mockMvc.get("/api/v1/me/notifications") { with(user("u1")) }.andExpect {
                jsonPath("$.content[0].campaignId") { value(third) }
                jsonPath("$.content[0].read") { value(true) }
                jsonPath("$.content[1].read") { value(false) }
            }

            // 알림을 눌러 열면 그 캠페인의 내 줄도 읽음
            mockMvc.post("/api/v1/push/campaigns/$second/opened") { with(user("u1")) }.andExpect { status { isNoContent() } }
            assertThat(unread("u1")).isEqualTo(1)
            mockMvc.get("/api/v1/me/notifications") { with(user("u1")) }.andExpect {
                jsonPath("$.content[1].id") { value(u1Second) }
                jsonPath("$.content[1].read") { value(true) }
                jsonPath("$.content[2].campaignId") { value(first) }
                jsonPath("$.content[2].read") { value(false) }
            }
            assertThat(unread("u2")).isEqualTo(3)

            // 모두 읽음은 내 것만
            mockMvc.post("/api/v1/me/notifications/read-all") { with(user("u1")) }.andExpect { status { isNoContent() } }
            assertThat(unread("u1")).isEqualTo(0)
            assertThat(unread("u2")).isEqualTo(3)
            mockMvc.get("/api/v1/me/notifications").andExpect { status { isUnauthorized() } }
        }

        /** 분 단위로 옮기는 KST 시계. */
        class PushClock : Clock() {
            private var now: Instant = Instant.now()

            fun set(
                year: Int,
                month: Int,
                day: Int,
                hour: Int,
                minute: Int,
            ) {
                now = LocalDateTime.of(year, month, day, hour, minute).atZone(KST).toInstant()
            }

            override fun getZone(): ZoneId = KST

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = now

            companion object {
                private val KST: ZoneId = ZoneId.of("Asia/Seoul")
            }
        }

        /** 보낸 것을 모두 적어 둔다. [invalid] 토큰은 실패(+무효)로, [error] 가 있으면 보내기 자체가 실패한다. */
        class RecordingPushSender : PushSender {
            val calls = mutableListOf<Pair<List<String>, PushMessage>>()
            var invalid: Set<String> = emptySet()
            var error: RuntimeException? = null

            fun reset() {
                calls.clear()
                invalid = emptySet()
                error = null
            }

            override fun send(
                tokens: List<String>,
                message: PushMessage,
            ): PushSendResult {
                error?.let { throw it }
                calls += tokens to message
                val bad = tokens.filter { it in invalid }
                return PushSendResult(tokens.size - bad.size, bad.size, bad, tokens.filter { it !in bad })
            }
        }

        @TestConfiguration
        class PushTestConfig {
            @Bean
            @Primary
            fun pushClock(): PushClock = PushClock()

            @Bean
            @Primary
            fun recordingPushSender(): RecordingPushSender = RecordingPushSender()
        }
    }
