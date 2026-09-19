package com.example.commerce.api.address

import com.example.commerce.api.support.CatalogTestSupport
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class AddressControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
    ) {
        private val me = jwt().jwt { it.subject("11") }

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        private fun create(body: String) =
            mockMvc.post("/api/v1/addresses") {
                with(me)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        @Test
        fun `첫 배송지는 기본이 되고 기본 변경 삭제 승격이 된다`() {
            val home =
                JsonPath.read<Int>(
                    create("""{"recipient":"임준섭","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구","address2":"101동"}""")
                        .andExpect {
                            status { isCreated() }
                            jsonPath("$.isDefault") { value(true) }
                        }.andReturn()
                        .response.contentAsString,
                    "$.id",
                )
            val office =
                JsonPath.read<Int>(
                    create("""{"recipient":"임준섭","phone":"02-000-0000","zipCode":"04524","address1":"서울 중구"}""")
                        .andExpect { jsonPath("$.isDefault") { value(false) } }
                        .andReturn()
                        .response.contentAsString,
                    "$.id",
                )

            mockMvc.put("/api/v1/addresses/$office/default") { with(me) }.andExpect { jsonPath("$.isDefault") { value(true) } }
            mockMvc.get("/api/v1/addresses") { with(me) }.andExpect {
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].id") { value(office) }
                jsonPath("$[1].isDefault") { value(false) }
            }

            mockMvc
                .put("/api/v1/addresses/$home") {
                    with(me)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"recipient":"임","phone":"010-1234-5678","zipCode":"06236","address1":"서울 강남구 테헤란로","isDefault":true}"""
                }.andExpect {
                    jsonPath("$.address1") { value("서울 강남구 테헤란로") }
                    jsonPath("$.isDefault") { value(true) }
                }

            // 기본을 지우면 남은 것 중 최근 것이 기본이 된다
            mockMvc.delete("/api/v1/addresses/$home") { with(me) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api/v1/addresses") { with(me) }.andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].isDefault") { value(true) }
            }
            mockMvc.delete("/api/v1/addresses/$home") { with(me) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `검증 메시지`() {
            create("""{"recipient":"","phone":"010","zipCode":"1","address1":""}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { exists() }
            }
            create("""{"recipient":"임","phone":"abc","zipCode":"06236","address1":"서울"}""").andExpect {
                jsonPath("$.message") { value("phone: 연락처는 숫자와 하이픈만 쓸 수 있습니다.") }
            }
        }
    }
