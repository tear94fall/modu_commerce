package com.example.commerce.api.coupon

import com.example.commerce.api.promotion.PromotionControllerTest
import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.point.PointGateway
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@Import(PromotionControllerTest.ClockTestConfig::class)
class CouponControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val clock: PromotionControllerTest.MovableClock,
    ) {
        @MockitoBean
        private lateinit var pointGateway: PointGateway

        private val me = jwt().jwt { it.subject("11") }
        private val other = jwt().jwt { it.subject("22") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() {
            support.reseed()
            clock.today = LocalDate.of(2026, 9, 25)
        }

        private fun postJson(
            url: String,
            user: RequestPostProcessor,
            body: String,
        ): ResultActionsDsl =
            mockMvc.post(url) {
                with(user)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        private fun coupon(body: String): Int =
            JsonPath.read(
                postJson("/api-admin/v1/coupons", admin, body)
                    .andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )

        /** 전체 상품 3,000원, 1만원 이상, 코드 WELCOME3000, 앱 받기. */
        private fun welcome(
            quantity: String = "null",
            code: String = "welcome3000",
        ): Int =
            coupon(
                """{"name":"웰컴 3천원","discountType":"FIXED","discountValue":3000,"minOrderAmount":10000,"scope":"ALL",
                   "issueStart":"2026-09-01","issueEnd":"2026-09-30","validDays":7,"totalQuantity":$quantity,
                   "code":"$code","downloadable":true,"active":true}""",
            )

        private fun myCouponId(user: RequestPostProcessor = me): Int =
            JsonPath.read(
                mockMvc
                    .get("/api/v1/me/coupons") { with(user) }
                    .andReturn()
                    .response.contentAsString,
                "$[0].id",
            )

        private fun order(
            couponId: Int?,
            vararg names: String,
            usePoints: Long = 0,
        ): ResultActionsDsl {
            val addressId =
                JsonPath.read<Int>(
                    postJson(
                        "/api/v1/addresses",
                        me,
                        """{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구","address2":null}""",
                    ).andReturn().response.contentAsString,
                    "$.id",
                )
            val items = names.joinToString(",") { """{"skuId":${support.skuId(it)},"quantity":1}""" }
            return postJson(
                "/api/v1/orders",
                me,
                """{"addressId":$addressId,"items":[$items],"usePoints":$usePoints,"userCouponId":${couponId ?: "null"}}""",
            )
        }

        @Test
        fun `download, duplicate, count and code redeem`() {
            val id = welcome()
            mockMvc.get("/api/v1/coupons/downloadable") { with(me) }.andExpect {
                jsonPath("$[0].couponId") { value(id) }
                jsonPath("$[0].scopeLabel") { value("전체 상품") }
                jsonPath("$[0].expiryLabel") { value("받은 날부터 7일") }
                jsonPath("$[0].downloaded") { value(false) }
            }
            mockMvc.post("/api/v1/coupons/$id/download") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("AVAILABLE") }
                jsonPath("$.source") { value("DOWNLOAD") }
                jsonPath("$.expiresOn") { value("2026-10-01") }
            }
            mockMvc.post("/api/v1/coupons/$id/download") { with(me) }.andExpect {
                status { isConflict() }
                jsonPath("$.message") { value("이미 받은 쿠폰입니다.") }
            }
            mockMvc.get("/api/v1/me/coupons/count") { with(me) }.andExpect { jsonPath("$.available") { value(1) } }
            mockMvc.get("/api/v1/coupons/downloadable") { with(me) }.andExpect { jsonPath("$[0].downloaded") { value(true) } }

            postJson("/api/v1/coupons/redeem", other, """{"code":" welcome3000 "}""").andExpect {
                status { isOk() }
                jsonPath("$.source") { value("CODE") }
            }
            postJson("/api/v1/coupons/redeem", other, """{"code":"NOPE1234"}""").andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("쿠폰 코드를 확인해 주세요.") }
            }
        }

        @Test
        fun `quantity, issue period and downloadable are enforced`() {
            val id = welcome(quantity = "1")
            mockMvc.post("/api/v1/coupons/$id/download") { with(me) }.andExpect { status { isOk() } }
            mockMvc.post("/api/v1/coupons/$id/download") { with(other) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("쿠폰이 모두 소진되었습니다.") }
            }
            mockMvc.get("/api/v1/coupons/downloadable") { with(other) }.andExpect { jsonPath("$[0].soldOut") { value(true) } }

            val later = welcome(code = "LATER3000")
            clock.today = LocalDate.of(2026, 10, 1)
            mockMvc.post("/api/v1/coupons/$later/download") { with(other) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("쿠폰을 받을 수 있는 기간이 아닙니다.") }
            }
            // 7일짜리를 9/25 에 받았으니 10/2 부터 만료
            clock.today = LocalDate.of(2026, 10, 2)
            mockMvc.get("/api/v1/me/coupons?status=EXPIRED") { with(me) }.andExpect { jsonPath("$.length()") { value(1) } }
        }

        @Test
        fun `checkout discount, points cap, use and restore on cancel`() {
            val fixed = welcome()
            val stationery =
                coupon(
                    """{"name":"문구 10%","discountType":"PERCENT","discountValue":10,"maxDiscount":1000,"scope":"CATEGORY",
                       "scopeIds":[${support.categoryId("문구")}],"issueStart":"2026-09-01","issueEnd":"2026-09-30",
                       "validUntil":"2026-10-31","downloadable":true,"active":true}""",
                )
            mockMvc.post("/api/v1/coupons/$fixed/download") { with(me) }
            mockMvc.post("/api/v1/coupons/$stationery/download") { with(me) }

            val sticker = support.skuId("모두 스티커 팩")
            postJson("/api/v1/coupons/applicable", me, """{"items":[{"skuId":$sticker,"quantity":1}]}""").andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("문구 10%") }
                jsonPath("$[0].discount") { value(500) }
                jsonPath("$[0].applicable") { value(true) }
                jsonPath("$[1].applicable") { value(false) }
                jsonPath("$[1].reason") { value("10,000원 이상 주문 시 사용 가능") }
            }

            val applicable =
                postJson("/api/v1/coupons/applicable", me, """{"items":[{"skuId":${support.skuId("모두 다이어리 2027")},"quantity":1}]}""")
                    .andReturn()
                    .response.contentAsString
            val fixedUserCoupon = JsonPath.read<List<Int>>(applicable, "$[?(@.name == '웰컴 3천원')].id")[0]

            order(fixedUserCoupon, "모두 다이어리 2027", usePoints = 12001).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("포인트는 결제할 금액(12000원)까지만 쓸 수 있습니다.") }
            }
            val body =
                order(fixedUserCoupon, "모두 다이어리 2027", usePoints = 2000)
                    .andExpect {
                        status { isCreated() }
                        jsonPath("$.totalAmount") { value(15000) }
                        jsonPath("$.couponDiscount") { value(3000) }
                        jsonPath("$.couponName") { value("웰컴 3천원") }
                        jsonPath("$.pointAmount") { value(2000) }
                        jsonPath("$.paymentAmount") { value(10000) }
                    }.andReturn()
                    .response.contentAsString
            mockMvc.get("/api/v1/me/coupons?status=USED") { with(me) }.andExpect { jsonPath("$[0].name") { value("웰컴 3천원") } }
            order(fixedUserCoupon, "모두 다이어리 2027").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("이미 사용했거나 기한이 지난 쿠폰입니다.") }
            }

            val orderId = JsonPath.read<Int>(body, "$.id")
            mockMvc.post("/api/v1/orders/$orderId/cancel") { with(me) }.andExpect { status { isOk() } }
            mockMvc.get("/api/v1/me/coupons") { with(me) }.andExpect { jsonPath("$[?(@.name == '웰컴 3천원')].status") { value("AVAILABLE") } }

            // 다른 사람 쿠폰은 못 쓴다
            order(myCouponId(me), "모두 다이어리 2027").andExpect { status { isCreated() } }
        }

        @Test
        fun `coupon event gives all event coupons once`() {
            val a = welcome()
            val b =
                coupon(
                    """{"name":"이벤트 전용 5%","discountType":"PERCENT","discountValue":5,"scope":"ALL","issueStart":"2026-09-01",
                       "issueEnd":"2026-09-30","validDays":30,"downloadable":false,"active":true}""",
                )
            val event =
                JsonPath.read<Int>(
                    postJson(
                        "/api-admin/v1/promotions",
                        admin,
                        """{"type":"EVENT","eventKind":"COUPON","title":"쿠폰 팩","startDate":"2026-09-20","endDate":"2026-09-30",
                           "visible":true,"couponIds":[$a,$b]}""",
                    ).andExpect { status { isCreated() } }
                        .andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            mockMvc.get("/api/v1/promotions/$event") { with(me) }.andExpect {
                jsonPath("$.eventKind") { value("COUPON") }
                jsonPath("$.attendance") { doesNotExist() }
                jsonPath("$.coupons.length()") { value(2) }
                jsonPath("$.coupons[1].downloaded") { value(false) }
            }
            mockMvc.post("/api/v1/coupons/$b/download") { with(me) }.andExpect { status { isBadRequest() } }

            mockMvc.post("/api/v1/promotions/$event/coupons") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.issued.length()") { value(2) }
                jsonPath("$.issued[1].source") { value("EVENT") }
                jsonPath("$.alreadyHad") { value(0) }
            }
            mockMvc.post("/api/v1/promotions/$event/coupons") { with(me) }.andExpect { status { isConflict() } }
            mockMvc.post("/api/v1/promotions/$event/attendance") { with(me) }.andExpect { status { isBadRequest() } }

            mockMvc.get("/api-admin/v1/promotions/$event") { with(admin) }.andExpect {
                jsonPath("$.eventKind") { value("COUPON") }
                jsonPath("$.coupons[0].issuedCount") { value(1) }
            }
            postJson(
                "/api-admin/v1/promotions",
                admin,
                """{"type":"EVENT","eventKind":"COUPON","title":"빈 쿠폰","startDate":"2026-09-20","endDate":"2026-09-30","visible":true}""",
            ).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("쿠폰 이벤트에 줄 쿠폰을 1~10개 고르세요.") }
            }
        }

        @Test
        fun `admin detail, validation, grant and issues`() {
            val id =
                coupon(
                    """{"name":"스티커 전용","discountType":"FIXED","discountValue":1000,"scope":"PRODUCT",
                       "scopeIds":[${support.productId("모두 스티커 팩")}],"issueStart":"2026-09-01","issueEnd":"2026-09-30",
                       "validUntil":"2026-12-31","downloadable":false,"active":true}""",
                )
            mockMvc.get("/api-admin/v1/coupons/$id") { with(admin) }.andExpect {
                jsonPath("$.name") { value("스티커 전용") }
                jsonPath("$.scopeLabel") { value("'모두 스티커 팩' 전용") }
                jsonPath("$.scopeTargets[0].name") { value("모두 스티커 팩") }
                jsonPath("$.issuedCount") { value(0) }
            }
            postJson("/api-admin/v1/coupons/$id/issues", admin, """{"userIds":["11","22","11"]}""").andExpect {
                jsonPath("$.issued") { value(2) }
            }
            postJson("/api-admin/v1/coupons/$id/issues", admin, """{"userIds":["11"]}""").andExpect {
                jsonPath("$.issued") { value(0) }
                jsonPath("$.skipped[0].reason") { value("이미 받은 쿠폰입니다.") }
            }
            mockMvc.get("/api-admin/v1/coupons/$id/issues?status=AVAILABLE") { with(admin) }.andExpect {
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.content[0].source") { value("ADMIN") }
            }
            mockMvc.get("/api-admin/v1/coupons?q=스티커") { with(admin) }.andExpect { jsonPath("$.content[0].issuedCount") { value(2) } }

            fun bad(
                body: String,
                message: String,
            ) = postJson("/api-admin/v1/coupons", admin, body).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value(message) }
            }
            val base = """"name":"x","issueStart":"2026-09-01","issueEnd":"2026-09-30","scope":"ALL""""
            bad("""{$base,"discountType":"PERCENT","discountValue":95,"validDays":3}""", "할인율은 1~90% 사이로 입력하세요.")
            bad("""{$base,"discountType":"FIXED","discountValue":1000}""", "사용 기한은 날짜 또는 받은 날부터 며칠 중 하나만 정하세요.")
            bad("""{$base,"discountType":"FIXED","discountValue":1000,"validDays":3,"code":"AB"}""", "쿠폰 코드는 영문 대문자·숫자 4~20자로 입력하세요.")
            welcome()
            bad("""{$base,"discountType":"FIXED","discountValue":1000,"validDays":3,"code":"WELCOME3000"}""", "이미 쓰는 쿠폰 코드입니다: WELCOME3000")
            mockMvc.get("/api-admin/v1/coupons") { with(me) }.andExpect { status { isForbidden() } }
        }
    }
