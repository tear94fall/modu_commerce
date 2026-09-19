package com.example.commerce.api.product

import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.seed.ProductSeeder
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
    ) {
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        @Test
        fun `목록은 최신 등록순 페이지이고 필터가 된다`() {
            mockMvc
                .get("/api-admin/v1/products") {
                    param("page", "0")
                    param("size", "2")
                    with(admin)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.content.length()") { value(2) }
                    jsonPath("$.content[0].name") { value("모두 스티커 팩") }
                    jsonPath("$.content[0].totalStock") { value(100) }
                    jsonPath("$.content[0].categoryName") { value("노트·데스크") }
                    jsonPath("$.totalElements") { value(ProductSeeder.SAMPLE_COUNT) }
                    jsonPath("$.totalPages") { value(12) }
                }
            mockMvc
                .get("/api-admin/v1/products") {
                    param("categoryId", support.categoryId("의류").toString())
                    with(admin)
                }.andExpect { jsonPath("$.totalElements") { value(2) } }
        }

        @Test
        fun `옛 본문으로 등록 수정 삭제 흐름`() {
            val created =
                mockMvc
                    .post("/api-admin/v1/products") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"name":"모두 우산","description":"자동 우산","price":15000,"imageUrl":"https://img/u.png"}"""
                    }.andExpect {
                        status { isCreated() }
                        header { exists("Location") }
                        jsonPath("$.name") { value("모두 우산") }
                        jsonPath("$.images[0]") { value("https://img/u.png") }
                        jsonPath("$.skus.length()") { value(1) }
                        jsonPath("$.skus[0].stock") { value(0) }
                        jsonPath("$.soldOut") { value(true) }
                        jsonPath("$.status") { value("SELLING") }
                    }.andReturn()
            val id = JsonPath.read<Int>(created.response.contentAsString, "$.id")

            mockMvc
                .put("/api-admin/v1/products/$id") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"모두 장우산","description":"튼튼한","price":21000,"imageUrl":null,"stock":7}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("모두 장우산") }
                    jsonPath("$.imageUrl") { isEmpty() }
                    jsonPath("$.images.length()") { value(0) }
                    jsonPath("$.skus[0].stock") { value(7) }
                }

            mockMvc.delete("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api/v1/products/$id") { with(jwt()) }.andExpect { status { isNotFound() } }
            mockMvc.delete("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `옵션 상품을 등록하고 수정해도 같은 조합의 SKU id 는 유지된다`() {
            val clothes = support.categoryId("의류")
            val body =
                """
                {"name":"모두 셔츠","description":"린넨","detail":"시원한 린넨 셔츠","price":30000,"listPrice":40000,"categoryId":$clothes,
                 "images":["https://img/s1.png","https://img/s2.png"],
                 "optionGroups":[{"name":"색상","values":["블랙","화이트"]},{"name":"사이즈","values":["M","L"]}],
                 "skus":[{"options":{"색상":"블랙","사이즈":"M"},"extraPrice":0,"stock":5},
                         {"options":{"색상":"블랙","사이즈":"L"},"extraPrice":1000,"stock":0},
                         {"options":{"색상":"화이트","사이즈":"M"},"extraPrice":0,"stock":2}]}
                """.trimIndent()
            val created =
                mockMvc
                    .post("/api-admin/v1/products") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = body
                    }.andExpect {
                        status { isCreated() }
                        jsonPath("$.discountRate") { value(25) }
                        jsonPath("$.categoryPath[1]") { value("의류") }
                        jsonPath("$.optionGroups.length()") { value(2) }
                        jsonPath("$.skus.length()") { value(3) }
                        jsonPath("$.soldOut") { value(false) }
                    }.andReturn()
            val json = created.response.contentAsString
            val id = JsonPath.read<Int>(json, "$.id")
            val blackMId = JsonPath.read<Int>(json, "$.skus[0].id")

            val updated =
                mockMvc
                    .put("/api-admin/v1/products/$id") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            body.replace(""""stock":5""", """"stock":9""").replace(
                                """{"options":{"색상":"화이트","사이즈":"M"},"extraPrice":0,"stock":2}""",
                                """{"options":{"색상":"화이트","사이즈":"L"},"extraPrice":0,"stock":1}""",
                            )
                    }.andReturn()
            check(updated.response.status == 200) { "PUT failed: ${updated.response.status} ${updated.response.contentAsString}" }
            val skus = JsonPath.read<List<Map<String, Any>>>(updated.response.contentAsString, "$.skus")
            val blackM = skus.first { it["optionLabel"] == "블랙 / M" }
            assertEquals(blackMId, blackM["id"])
            assertEquals(9, blackM["stock"])
            assertEquals(null, skus.firstOrNull { it["optionLabel"] == "화이트 / M" })

            // 앱 상세에서도 같은 구조로 보인다
            mockMvc
                .get("/api/v1/products/$id") { with(jwt()) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.skus.length()") { value(3) }
                }
        }

        @Test
        fun `검증에 실패하면 400 과 이유`() {
            fun postExpecting(
                body: String,
                message: String,
            ) {
                mockMvc
                    .post("/api-admin/v1/products") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = body
                    }.andExpect {
                        status { isBadRequest() }
                        jsonPath("$.message") { value(message) }
                    }
            }
            postExpecting("""{"name":"  ","price":1}""", "name: 상품 이름을 입력해 주세요.")
            postExpecting("""{"name":"우산","price":-1}""", "price: 가격은 0 이상이어야 합니다.")
            postExpecting("""{"name":"우산"}""", "price: 가격을 입력해 주세요.")
            postExpecting(
                """{"name":"우산","price":1,"imageUrl":"ftp://img/u.png"}""",
                "imageUrl: 이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.",
            )
            postExpecting("""{"name":"우산","price":10000,"listPrice":9000}""", "정가는 판매가 이상이어야 합니다.")
            postExpecting(
                """{"name":"우산","price":1,"optionGroups":[{"name":"색상","values":["블랙"]}],"skus":[{"options":{"색상":"레드"}}]}""",
                "옵션 '색상' 에 '레드' 값이 없습니다.",
            )
            postExpecting(
                """{"name":"우산","price":1,"optionGroups":[{"name":"색상","values":["블랙"]}],"skus":[]}""",
                "옵션 조합(SKU)을 하나 이상 입력해 주세요.",
            )
            postExpecting("not json", "요청 본문을 읽을 수 없습니다.")
        }

        @Test
        fun `없는 카테고리는 404`() {
            mockMvc
                .post("/api-admin/v1/products") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"우산","price":1,"categoryId":999999}"""
                }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `빈 설명과 빈 이미지 주소는 빈 문자열과 null 로 저장한다`() {
            mockMvc
                .post("/api-admin/v1/products") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":" 모두 우산 ","price":0,"imageUrl":""}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.name") { value("모두 우산") }
                    jsonPath("$.description") { value("") }
                    jsonPath("$.imageUrl") { isEmpty() }
                }
        }
    }
