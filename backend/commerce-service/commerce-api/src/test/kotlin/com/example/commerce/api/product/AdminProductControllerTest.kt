package com.example.commerce.api.product

import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.seed.ProductSeeder
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
class AdminProductControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val productRwRepository: ProductRwRepository,
    ) {
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        /** 같은 스프링 컨텍스트(H2)를 ProductControllerTest 와 나눠 쓰므로 시드 4개 상태로 되돌려 둔다. */
        @BeforeEach
        @AfterEach
        fun reseed() {
            productRwRepository.deleteAll()
            productRwRepository.saveAll(ProductSeeder.sampleProducts())
        }

        @Test
        fun `목록은 최신 등록순 페이지로 준다`() {
            mockMvc
                .get("/api-admin/v1/products") {
                    param("page", "0")
                    param("size", "2")
                    with(admin)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.content.length()") { value(2) }
                    jsonPath("$.content[0].name") { value("모두 기계식 키보드") }
                    jsonPath("$.totalElements") { value(4) }
                    jsonPath("$.totalPages") { value(2) }
                    jsonPath("$.number") { value(0) }
                    jsonPath("$.size") { value(2) }
                }
        }

        @Test
        fun `등록 수정 삭제 흐름`() {
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
                    }.andReturn()
            val id = JsonPath.read<Int>(created.response.contentAsString, "$.id")

            mockMvc
                .put("/api-admin/v1/products/$id") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"모두 장우산","description":"튼튼한","price":21000,"imageUrl":null}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("모두 장우산") }
                    jsonPath("$.imageUrl") { isEmpty() }
                }

            mockMvc.delete("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api/v1/products/$id") { with(jwt()) }.andExpect { status { isNotFound() } }
            mockMvc.delete("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `검증에 실패하면 400 과 필드별 이유`() {
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
            postExpecting("not json", "요청 본문을 읽을 수 없습니다.")
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
