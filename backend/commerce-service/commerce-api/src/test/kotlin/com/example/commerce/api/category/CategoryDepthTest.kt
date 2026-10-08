package com.example.commerce.api.category

import com.example.commerce.api.promotion.PromotionControllerTest
import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.service.CategoryQueryService
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDate

/** 3단계 카테고리: 깊이 제한, 옮기기, 형제 순서, 재귀 트리, 하위 상품·쿠폰 범위, 전체 경로. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PromotionControllerTest.ClockTestConfig::class)
class CategoryDepthTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        private val categoryQueryService: CategoryQueryService,
        private val clock: PromotionControllerTest.MovableClock,
    ) {
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
        private val me = jwt().jwt { it.subject("11") }

        @BeforeEach
        @AfterEach
        fun reseed() {
            support.reseed()
            clock.today = LocalDate.of(2026, 9, 25)
        }

        private fun send(
            method: String,
            url: String,
            body: String,
        ): ResultActionsDsl {
            val init: org.springframework.test.web.servlet.MockHttpServletRequestDsl.() -> Unit = {
                with(admin)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
            return if (method == "PUT") mockMvc.put(url, dsl = init) else mockMvc.post(url, dsl = init)
        }

        private fun create(
            name: String,
            parentId: Long?,
            sortOrder: Int? = null,
        ): Long =
            JsonPath
                .read<Int>(
                    send("POST", "/api-admin/v1/categories", """{"name":"$name","parentId":$parentId,"sortOrder":$sortOrder}""")
                        .andExpect { status { isCreated() } }
                        .andReturn()
                        .response.contentAsString,
                    "$.id",
                ).toLong()

        @Test
        fun `3단계까지 만들고 4단계는 400, sortOrder 가 없으면 형제 맨 뒤`() {
            val desk = support.categoryId("노트·데스크")
            val notes = create("노트", desk)
            val pens = create("필기구", desk)
            val explicit = create("지정 순서", desk, sortOrder = 0)

            send("POST", "/api-admin/v1/categories", """{"name":"4단계","parentId":$notes}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("카테고리는 3단계까지만 만들 수 있습니다.") }
            }

            mockMvc.get("/api-admin/v1/categories") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$[3].name") { value("문구") }
                jsonPath("$[3].depth") { value(1) }
                jsonPath("$[3].children[0].depth") { value(2) }
                // 노트는 형제가 없어 0, 필기구는 그 뒤 1, 지정 순서는 0 을 직접 줬다 → (sortOrder, id) 순으로 노트, 지정 순서, 필기구.
                jsonPath("$[3].children[0].children.length()") { value(3) }
                jsonPath("$[3].children[0].children[0].id") { value(notes) }
                jsonPath("$[3].children[0].children[0].sortOrder") { value(0) }
                jsonPath("$[3].children[0].children[0].depth") { value(3) }
                jsonPath("$[3].children[0].children[0].productCount") { value(0) }
                jsonPath("$[3].children[0].children[1].id") { value(explicit) }
                jsonPath("$[3].children[0].children[2].id") { value(pens) }
                jsonPath("$[3].children[0].children[2].sortOrder") { value(1) }
                jsonPath("$[3].children[0].children[2].children.length()") { value(0) }
            }
            // 앱 트리도 같은 깊이로 내려간다(상품 수는 없음).
            mockMvc.get("/api-public/v1/categories") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$[3].children[0].children[2].name") { value("필기구") }
                jsonPath("$[3].children[0].children[2].depth") { value(3) }
                jsonPath("$[3].children[0].children[2].productCount") { doesNotExist() }
            }
            // 최상위도 맨 뒤(문구 3 다음 4).
            val root = create("새 대분류", null)
            mockMvc.get("/api-admin/v1/categories") { with(admin) }.andExpect {
                jsonPath("$[4].id") { value(root) }
                jsonPath("$[4].sortOrder") { value(4) }
            }
        }

        @Test
        fun `옮기기 - 순환과 깊이 초과는 400, 하위가 있어도 깊이가 맞으면 옮긴다`() {
            val fashion = support.categoryId("패션")
            val stationery = support.categoryId("문구")
            val desk = support.categoryId("노트·데스크")
            val notes = create("노트", desk)

            // 자기 자신 아래, 자기 하위 아래
            send("PUT", "/api-admin/v1/categories/$stationery", """{"name":"문구","parentId":$stationery}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("카테고리를 자기 자신이나 하위 카테고리 아래로 옮길 수 없습니다.") }
            }
            send("PUT", "/api-admin/v1/categories/$stationery", """{"name":"문구","parentId":$notes}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("카테고리를 자기 자신이나 하위 카테고리 아래로 옮길 수 없습니다.") }
            }
            // 문구(높이 3)를 패션 아래로 → 4단계
            send("PUT", "/api-admin/v1/categories/$stationery", """{"name":"문구","parentId":$fashion}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("옮기면 하위 카테고리까지 4단계가 됩니다. 카테고리는 3단계까지만 만들 수 있습니다.") }
            }
            // 노트·데스크(높이 2)를 패션 아래로 → 3단계, 하위(노트)도 따라간다. sortOrder 없으면 새 형제 맨 뒤(가방0 의류1 모자2 → 3).
            send("PUT", "/api-admin/v1/categories/$desk", """{"name":"노트·데스크","parentId":$fashion}""").andExpect {
                status { isOk() }
                jsonPath("$.parentId") { value(fashion) }
                jsonPath("$.sortOrder") { value(3) }
            }
            mockMvc.get("/api-admin/v1/categories") { with(admin) }.andExpect {
                jsonPath("$[2].children[3].name") { value("노트·데스크") }
                jsonPath("$[2].children[3].children[0].name") { value("노트") }
                jsonPath("$[2].children[3].children[0].depth") { value(3) }
                jsonPath("$[3].children.length()") { value(0) }
            }
            // 노트·데스크를 다시 최상위로: 2단계 서브트리라 된다. 같은 부모 안에서 sortOrder 를 빼면 그대로 둔다.
            send("PUT", "/api-admin/v1/categories/$desk", """{"name":"노트·데스크","parentId":null}""").andExpect { status { isOk() } }
            send("PUT", "/api-admin/v1/categories/$desk", """{"name":"데스크","parentId":null}""").andExpect {
                status { isOk() }
                jsonPath("$.sortOrder") { value(4) }
            }
        }

        @Test
        fun `형제 순서 바꾸기 - 성공, 목록이 다르면 400, 없는 부모 404`() {
            val fashion = support.categoryId("패션")
            val bag = support.categoryId("가방")
            val clothes = support.categoryId("의류")
            val hat = support.categoryId("모자")

            send("PUT", "/api-admin/v1/categories/order", """{"parentId":$fashion,"ids":[$hat,$bag,$clothes]}""").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(4) }
                jsonPath("$[2].children[0].name") { value("모자") }
                jsonPath("$[2].children[0].sortOrder") { value(0) }
                jsonPath("$[2].children[1].name") { value("가방") }
                jsonPath("$[2].children[2].name") { value("의류") }
                jsonPath("$[2].children[2].sortOrder") { value(2) }
                jsonPath("$[2].children[2].productCount") { value(2) }
                jsonPath("$[2].children[2].depth") { value(2) }
            }

            val mismatch = "순서를 바꿀 카테고리 목록이 현재 목록과 다릅니다. 새로고침 후 다시 시도하세요."
            listOf(
                "[$hat,$bag]",
                "[$hat,$bag,$bag]",
                "[$hat,$bag,$clothes,${support.categoryId("주방")}]",
                "[$hat,$bag,${support.categoryId("주방")}]",
            ).forEach { ids ->
                send("PUT", "/api-admin/v1/categories/order", """{"parentId":$fashion,"ids":$ids}""").andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value(mismatch) }
                }
            }
            send("PUT", "/api-admin/v1/categories/order", """{"parentId":999999,"ids":[]}""").andExpect { status { isNotFound() } }

            // 최상위(parentId 없음): 문구를 맨 앞으로
            val roots = listOf("문구", "전자기기", "생활", "패션").map { support.categoryId(it) }
            send("PUT", "/api-admin/v1/categories/order", """{"ids":${roots.joinToString(",", "[", "]")}}""").andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("문구") }
                jsonPath("$[3].name") { value("패션") }
                jsonPath("$[3].children[0].name") { value("모자") }
            }
            mockMvc.get("/api-public/v1/categories") { with(me) }.andExpect { jsonPath("$[0].name") { value("문구") } }
        }

        @Test
        fun `손주 카테고리 상품 - 상위로 거른 목록, 전체 경로, 쿠폰 범위`() {
            val stationery = support.categoryId("문구")
            val desk = support.categoryId("노트·데스크")
            val notes = create("노트", desk)
            support.moveProduct("모두 하드커버 노트", notes)
            val product = support.productId("모두 하드커버 노트")

            assertEquals(listOf(stationery, desk, notes), categoryQueryService.subtreeIds(stationery))
            assertEquals(listOf(desk, notes), categoryQueryService.subtreeIds(desk))

            mockMvc
                .get("/api-public/v1/products") {
                    with(me)
                    param("categoryId", stationery.toString())
                    param("size", "50")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.content[?(@.name == '모두 하드커버 노트')]") { isNotEmpty() }
                    jsonPath("$.totalElements") { value(6) }
                }
            mockMvc.get("/api-public/v1/products/$product") { with(me) }.andExpect {
                jsonPath("$.categoryPath.length()") { value(3) }
                jsonPath("$.categoryPath[2]") { value("노트") }
            }
            mockMvc
                .get("/api-admin/v1/products") {
                    with(admin)
                    param("q", "하드커버")
                }.andExpect { jsonPath("$.content[0].categoryName") { value("문구 > 노트·데스크 > 노트") } }

            // 최상위(문구) 범위 쿠폰은 손주 카테고리 상품에도 쓴다. 다른 갈래(전자기기) 쿠폰은 못 쓴다.
            listOf("문구", "전자기기").forEach { root ->
                send(
                    "POST",
                    "/api-admin/v1/coupons",
                    """{"name":"$root 10%","discountType":"PERCENT","discountValue":10,"scope":"CATEGORY",
                       "scopeIds":[${support.categoryId(root)}],"issueStart":"2026-09-01","issueEnd":"2026-09-30",
                       "validUntil":"2026-10-31","downloadable":true,"active":true}""",
                ).andExpect { status { isCreated() } }
            }
            mockMvc
                .get("/api-public/v1/coupons/downloadable") {
                    with(me)
                    param("productId", product.toString())
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].name") { value("문구 10%") }
                }
        }
    }
