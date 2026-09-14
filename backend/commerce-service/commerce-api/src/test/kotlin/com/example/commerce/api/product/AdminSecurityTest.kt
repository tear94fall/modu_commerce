package com.example.commerce.api.product

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `토큰이 없으면 401`() {
            mockMvc.get("/api-admin/v1/products").andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `ROLE_ADMIN 이 없으면 403`() {
            mockMvc
                .get("/api-admin/v1/products") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }
                .andExpect { status { isForbidden() } }
        }
    }
