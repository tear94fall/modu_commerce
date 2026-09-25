package com.example.commerce.api.point

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class MyPointControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var pointClient: PointClient

        private val me = jwt().jwt { it.subject("11") }

        @Test
        fun `토큰의 사용자로 잔액을 조회한다`() {
            given(pointClient.balance("11")).willReturn(PointBalance("11", 580))

            mockMvc.get("/api/v1/me/points") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.balance") { value(580) }
            }
        }

        @Test
        fun `원장은 페이지 크기를 1~100 으로 자른다`() {
            given(pointClient.history("11", 0, 100)).willReturn(PointHistoryPage(emptyList(), 0, 0, 0, 100))

            mockMvc.get("/api/v1/me/points/history?page=-3&size=500") { with(me) }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(0) }
            }
        }

        @Test
        fun `포인트 서비스를 못 부르면 503`() {
            given(pointClient.balance("11")).willThrow(PointUnavailableException())

            mockMvc.get("/api/v1/me/points") { with(me) }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.message") { value("포인트 서비스에 연결할 수 없습니다.") }
            }
        }

        @Test
        fun `토큰이 없으면 401`() {
            mockMvc.get("/api/v1/me/points").andExpect { status { isUnauthorized() } }
        }
    }
