package com.example.commerce.api.replica

import com.example.commerce.api.promotion.PromotionControllerTest
import com.example.commerce.api.support.CatalogTestSupport
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
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
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * 레플리카가 복제를 멈춘(무한히 늦은) 상태에서 "쓰고 바로 읽는" 흐름이 깨지지 않는지 본다.
 *
 * 레플리카는 master 와 다른 H2 DB 다. 테스트마다 시드 직후의 master 를 통째로(스키마+데이터) 복사해 두고, 그 뒤로는
 * 아무것도 복제하지 않는다. 그래서 테스트 안에서 쓴 값은 master 에만 있다 — 응답·가드·캐시 채우기가 레플리카를 읽으면
 * 404·403·옛값으로 드러난다.
 */
@SpringBootTest(
    properties = ["spring.datasource.replica.url=jdbc:h2:mem:lagging_replica;MODE=MYSQL;DB_CLOSE_DELAY=-1"],
)
@AutoConfigureMockMvc
@Import(PromotionControllerTest.ClockTestConfig::class)
class ReplicaLagTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        @Qualifier("rwDataSource") masterDataSource: DataSource,
        @Qualifier("roHikariDataSource") replicaDataSource: DataSource,
    ) {
        private val master = JdbcTemplate(masterDataSource)
        private val replica = JdbcTemplate(replicaDataSource)
        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        fun freezeReplica() {
            support.reseed()
            val script = master.queryForList("SCRIPT", String::class.java)
            replica.execute("DROP ALL OBJECTS")
            script.forEach { replica.execute(it) }
        }

        @AfterEach
        fun reseed() = support.reseed()

        private fun replicaCount(table: String): Long =
            requireNotNull(replica.queryForObject("select count(*) from $table", Long::class.java))

        private fun postJson(
            path: String,
            body: String,
            auth: org.springframework.test.web.servlet.request.RequestPostProcessor = admin,
        ): ResultActionsDsl =
            mockMvc.post(path) {
                with(auth)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        private fun putJson(
            path: String,
            body: String,
        ): ResultActionsDsl =
            mockMvc.put(path) {
                with(admin)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        private fun idOf(result: ResultActionsDsl): Int = JsonPath.read(result.andReturn().response.contentAsString, "$.id")

        @Test
        fun `joining then calling a customer-only API works while the replica has no customer row`() {
            val newbie = jwt().jwt { it.subject("77") }
            postJson("/api-public/v1/me/customer", """{"agreeTerms":true,"agreePrivacy":true}""", newbie).andExpect {
                status { isCreated() }
                jsonPath("$.userId") { value("77") }
                jsonPath("$.tier.code") { value("WELCOME") }
            }
            assertThat(replica.queryForObject("select count(*) from commerce_customers where user_id = '77'", Long::class.java)).isZero()

            mockMvc.get("/api-public/v1/me/customer") { with(newbie) }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value("77") }
            }
            // @CustomerRequired 가드가 master 를 본다(레플리카를 보면 403 CUSTOMER_REQUIRED).
            mockMvc.get("/api-public/v1/cart") { with(newbie) }.andExpect { status { isOk() } }
        }

        @Test
        fun `cart, address and a just-placed order read back fresh`() {
            val addressId =
                idOf(
                    postJson(
                        "/api-public/v1/addresses",
                        """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구"}""",
                        me,
                    ).andExpect { status { isCreated() } },
                )
            mockMvc.get("/api-public/v1/addresses") { with(me) }.andExpect { jsonPath("$[*].id") { value(hasItem(addressId)) } }

            val sku = support.skuId("모두 머그컵 세트")
            val cartItemId =
                idOf(postJson("/api-public/v1/cart/items", """{"skuId":$sku,"quantity":1}""", me).andExpect { status { isOk() } })
            mockMvc
                .patch("/api-public/v1/cart/items/$cartItemId") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"quantity":3}"""
                }.andExpect { status { isOk() } }
            // 결제 화면은 GET /cart 의 수량으로 주문한다 — 옛 수량(1)이 보이면 안 된다.
            mockMvc.get("/api-public/v1/cart") { with(me) }.andExpect {
                jsonPath("$.items[0].id") { value(cartItemId) }
                jsonPath("$.items[0].quantity") { value(3) }
            }
            assertThat(replicaCount("cart_items")).isZero()

            val orderId =
                idOf(
                    postJson(
                        "/api-public/v1/orders",
                        """{"addressId":$addressId,"items":[{"skuId":$sku,"quantity":3}],"cartItemIds":[$cartItemId]}""",
                        me,
                    ).andExpect { status { isCreated() } },
                )
            assertThat(replicaCount("orders")).isZero()
            // 결제하자마자 주문 상세로 넘어간다 — 레플리카에 없으면 master 에서 읽는다.
            mockMvc.get("/api-public/v1/orders/$orderId") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(orderId) }
                jsonPath("$.status") { value("PAID") }
            }
            mockMvc.get("/api-public/v1/orders/$orderId") { with(jwt().jwt { it.subject("22") }) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api-public/v1/orders/999999") { with(me) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api-public/v1/cart") { with(me) }.andExpect { jsonPath("$.itemCount") { value(0) } }
        }

        @Test
        fun `admin coupon create, update, grant and a coupon event answer from master`() {
            fun body(name: String) =
                """{"name":"$name","discountType":"FIXED","discountValue":3000,"minOrderAmount":0,"scope":"ALL",
                   "issueStart":"2026-09-01","issueEnd":"2026-09-30","validDays":30,"downloadable":false,"active":true}"""
            val couponId =
                idOf(
                    postJson("/api-admin/v1/coupons", body("가을 쿠폰")).andExpect {
                        status { isCreated() }
                        jsonPath("$.name") { value("가을 쿠폰") }
                    },
                )
            putJson("/api-admin/v1/coupons/$couponId", body("가을 쿠폰 2")).andExpect {
                status { isOk() }
                jsonPath("$.name") { value("가을 쿠폰 2") }
            }
            postJson("/api-admin/v1/coupons/$couponId/issues", """{"userIds":["22"]}""").andExpect {
                status { isOk() }
                jsonPath("$.issued") { value(1) }
            }

            val event =
                idOf(
                    postJson(
                        "/api-admin/v1/promotions",
                        """{"type":"EVENT","eventKind":"COUPON","title":"쿠폰 팩","startDate":"2026-09-20","endDate":"2026-09-30",
                           "visible":true,"couponIds":[$couponId]}""",
                    ).andExpect { status { isCreated() } },
                )
            postJson("/api-public/v1/promotions/$event/coupons", "", me).andExpect {
                status { isOk() }
                jsonPath("$.issued.length()") { value(1) }
            }
            assertThat(replicaCount("coupons")).isZero()
        }

        @Test
        fun `promotion create and update answer from master and the app cache is filled from master`() {
            val products = listOf("모두 스티커 팩", "모두 다이어리 2027").map { support.productId(it) }

            fun body(title: String) =
                """{"type":"EXHIBITION","title":"$title","startDate":"2026-09-20","endDate":"2026-09-30",
                   "visible":true,"productIds":[${products.joinToString()}]}"""
            val id =
                idOf(
                    postJson("/api-admin/v1/promotions", body("가을 기획전")).andExpect {
                        status { isCreated() }
                        jsonPath("$.title") { value("가을 기획전") }
                        jsonPath("$.products.length()") { value(2) }
                    },
                )
            putJson("/api-admin/v1/promotions/$id", body("가을 기획전 2")).andExpect {
                status { isOk() }
                jsonPath("$.title") { value("가을 기획전 2") }
            }
            // 백오피스 변경이 캐시를 비운 뒤 앱이 처음 읽을 때 레플리카(없음·옛값)로 캐시를 채우면 TTL 내내 남는다.
            mockMvc.get("/api-public/v1/promotions/banners") { with(me) }.andExpect { jsonPath("$[0].title") { value("가을 기획전 2") } }
            mockMvc.get("/api-public/v1/promotions/$id") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.title") { value("가을 기획전 2") }
            }
            assertThat(replicaCount("promotions")).isZero()
        }

        @Test
        fun `tier settings, tier run, push campaign and category answer from master`() {
            putJson(
                "/api-admin/v1/tiers",
                """[
                    {"code":"WELCOME","name":"새싹","color":"#64748B","minAmount":0,"earnRate":1,"couponIds":[]},
                    {"code":"SILVER","name":"실버","color":"#94A3B8","minAmount":100000,"earnRate":2,"couponIds":[]},
                    {"code":"GOLD","name":"골드","color":"#D97706","minAmount":300000,"earnRate":3,"couponIds":[]},
                    {"code":"VIP","name":"VIP","color":"#7C3AED","minAmount":700000,"earnRate":5,"couponIds":[]}
                ]""",
            ).andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("새싹") }
            }
            mockMvc.post("/api-admin/v1/tiers/runs") { with(admin) }.andExpect {
                status { isAccepted() }
                jsonPath("$.status") { value("DONE") }
            }

            val campaign =
                idOf(
                    postJson(
                        "/api-admin/v1/push-campaigns",
                        """{"title":"특가","body":"보러가기","targetType":"HOME","scheduledAt":"2026-09-30T12:00:00+09:00"}""",
                    ).andExpect {
                        status { isCreated() }
                        jsonPath("$.status") { value("SCHEDULED") }
                    },
                )
            mockMvc.post("/api-admin/v1/push-campaigns/$campaign/cancel") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELED") }
            }

            postJson("/api-admin/v1/categories", """{"name":"캠핑"}""").andExpect { status { isCreated() } }
            mockMvc.get("/api-admin/v1/categories") { with(admin) }.andExpect { jsonPath("$[*].name") { value(hasItem("캠핑")) } }
            assertThat(replicaCount("push_campaigns")).isZero()
        }

        @Test
        fun `push consent right after joining and the unread badge right after reading come from master`() {
            val newbie = jwt().jwt { it.subject("77") }
            postJson("/api-public/v1/me/customer", """{"agreeTerms":true,"agreePrivacy":true,"marketing":true}""", newbie)
                .andExpect { status { isCreated() } }
            assertThat(replicaCount("push_consents")).isZero()
            // 알림 설정 화면이 가입 직후 다시 읽는다 — 레플리카를 보면 동의 안 함(기본값)으로 보인다.
            mockMvc.get("/api-public/v1/me/push/consent") { with(newbie) }.andExpect {
                status { isOk() }
                jsonPath("$.marketing") { value(true) }
            }

            // 복제된 알림 한 줄(양쪽에 있음)을 읽음으로 바꾼다. 레플리카에는 여전히 안 읽음이다.
            val insert = "insert into push_inbox_items (id, user_id, campaign_id, created_at) values (?, '11', ?, ?)"
            val received = LocalDateTime.of(2026, 9, 25, 1, 0)
            listOf(master, replica).forEach { it.update(insert, 9001, 1, received) }
            mockMvc.post("/api-public/v1/me/notifications/9001/read") { with(me) }.andExpect { status { is2xxSuccessful() } }
            assertThat(replica.queryForObject("select count(*) from push_inbox_items where read_at is null", Long::class.java)).isEqualTo(1)
            mockMvc.get("/api-public/v1/me/notifications/unread-count") { with(me) }.andExpect { jsonPath("$.unread") { value(0) } }

            // 방금 받은 푸시(아직 master 에만 있음)도 뱃지에 바로 센다.
            master.update(insert, 9002, 2, received)
            mockMvc.get("/api-public/v1/me/notifications/unread-count") { with(me) }.andExpect { jsonPath("$.unread") { value(1) } }
        }
    }
