package com.example.commerce.api.category

import com.example.commerce.api.support.CatalogTestSupport
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
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
class AdminCategoryControllerTest
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
        fun `icon and colour are saved, shown to the app and validated`() {
            val body =
                mockMvc
                    .post("/api-admin/v1/categories") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"name":"캠핑","icon":"⛺","color":"#d1fae5"}"""
                    }.andExpect {
                        status { isCreated() }
                        jsonPath("$.icon") { value("⛺") }
                        jsonPath("$.color") { value("#D1FAE5") }
                    }.andReturn()
                    .response.contentAsString
            val id = JsonPath.read<Int>(body, "$.id")
            mockMvc.get("/api/v1/categories") { with(jwt().jwt { it.subject("11") }) }.andExpect {
                jsonPath("$[?(@.id == $id)].icon") { value("⛺") }
                jsonPath("$[?(@.name == '문구')].icon") { value("✏️") }
            }
            mockMvc
                .put("/api-admin/v1/categories/$id") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"캠핑","icon":"","color":"green"}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("아이콘 색은 #RRGGBB 형식으로 입력하세요.") }
                }
            mockMvc
                .put("/api-admin/v1/categories/$id") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"캠핑","icon":"","color":""}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.icon") { doesNotExist() }
                }
        }

        @Test
        fun `앱 토큰으로는 어드민 카테고리를 못 본다`() {
            mockMvc.get("/api-admin/v1/categories") { with(jwt()) }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `트리에 상품 수가 붙는다`() {
            mockMvc
                .get("/api-admin/v1/categories") { with(admin) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(4) }
                    jsonPath("$[2].name") { value("패션") }
                    jsonPath("$[2].productCount") { value(0) }
                    jsonPath("$[2].children[1].name") { value("의류") }
                    jsonPath("$[2].children[1].productCount") { value(2) }
                }
        }

        @Test
        fun `등록 수정 삭제와 2단계 규칙`() {
            val fashion = support.categoryId("패션")
            val clothes = support.categoryId("의류")

            val created =
                mockMvc
                    .post("/api-admin/v1/categories") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"name":"신발","parentId":$fashion,"sortOrder":9}"""
                    }.andExpect {
                        status { isCreated() }
                        jsonPath("$.parentId") { value(fashion) }
                    }.andReturn()
            val shoes = JsonPath.read<Int>(created.response.contentAsString, "$.id")

            // 하위 아래에는 만들 수 없다
            mockMvc
                .post("/api-admin/v1/categories") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"운동화","parentId":$shoes}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("카테고리는 2단계까지만 만들 수 있습니다.") }
                }

            mockMvc
                .put("/api-admin/v1/categories/$shoes") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"슈즈","parentId":$fashion,"sortOrder":1}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("슈즈") }
                }

            // 상품이 있는 카테고리는 못 지운다
            mockMvc.delete("/api-admin/v1/categories/$clothes") { with(admin) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("상품이 있는 카테고리는 삭제할 수 없습니다.") }
            }
            // 하위가 있는 카테고리도 못 지운다
            mockMvc.delete("/api-admin/v1/categories/$fashion") { with(admin) }.andExpect { status { isBadRequest() } }

            mockMvc.delete("/api-admin/v1/categories/$shoes") { with(admin) }.andExpect { status { isNoContent() } }
            mockMvc.delete("/api-admin/v1/categories/$shoes") { with(admin) }.andExpect { status { isNotFound() } }

            mockMvc
                .post("/api-admin/v1/categories") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":" "}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("name: 카테고리 이름을 입력해 주세요.") }
                }
        }
    }
