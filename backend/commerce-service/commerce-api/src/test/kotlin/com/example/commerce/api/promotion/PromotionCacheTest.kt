package com.example.commerce.api.promotion

import com.example.commerce.api.config.CacheConfig
import com.example.commerce.api.support.CatalogTestSupport
import com.example.commerce.application.domain.entity.EventKind
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.service.PromotionSnapshot
import com.example.commerce.application.usecase.result.PromotionBannerResult
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 기획전·이벤트 캐시(테스트는 메모리 캐시, 개발·운영은 Redis 클러스터 — 같은 @Cacheable 경로).
 * DB 를 직접 바꿔도 캐시된 값이 나오고, 백오피스로 바꾸면 바로 새 값이 나오는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PromotionControllerTest.ClockTestConfig::class)
class PromotionCacheTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val support: CatalogTestSupport,
        @Qualifier("rwDataSource") dataSource: DataSource,
    ) {
        private val jdbc = JdbcTemplate(dataSource)
        private val me = jwt().jwt { it.subject("11") }
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        @BeforeEach
        @AfterEach
        fun reseed() = support.reseed()

        private fun exhibition(title: String): Int {
            val ids = listOf("모두 스티커 팩", "모두 다이어리 2027").map { support.productId(it) }
            return JsonPath.read(
                mockMvc
                    .post("/api-admin/v1/promotions") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"type":"EXHIBITION","title":"$title","startDate":"2026-09-20","endDate":"2026-09-30",
                               "visible":true,"productIds":[${ids.joinToString()}]}"""
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )
        }

        @Test
        fun `banners and detail are served from the cache until the back office changes them`() {
            val id = exhibition("가을 기획전")
            mockMvc.get("/api/v1/promotions/banners") { with(me) }.andExpect { jsonPath("$[0].title") { value("가을 기획전") } }
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect { jsonPath("$.title") { value("가을 기획전") } }

            // 백오피스를 거치지 않은 변경은 TTL 이 지날 때까지 캐시가 보여 준다(= 캐시에서 읽는다).
            jdbc.update("update promotions set title = '몰래 바꿈' where id = ?", id)
            mockMvc.get("/api/v1/promotions/banners") { with(me) }.andExpect { jsonPath("$[0].title") { value("가을 기획전") } }
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect { jsonPath("$.title") { value("가을 기획전") } }

            // 백오피스 고치기는 배너·상세 캐시를 비운다.
            mockMvc
                .put("/api-admin/v1/promotions/$id") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"type":"EXHIBITION","title":"겨울 기획전","startDate":"2026-09-20","endDate":"2026-09-30","visible":true,
                           "productIds":[${support.productId("모두 스티커 팩")}]}"""
                }.andExpect { status { isOk() } }
            mockMvc.get("/api/v1/promotions/banners") { with(me) }.andExpect { jsonPath("$[0].title") { value("겨울 기획전") } }
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect {
                jsonPath("$.title") { value("겨울 기획전") }
                jsonPath("$.products.length()") { value(1) }
            }
        }

        @Test
        fun `new, hidden and deleted promotions show up right away`() {
            mockMvc.get("/api/v1/promotions/banners") { with(me) }.andExpect { jsonPath("$.length()") { value(0) } }
            val id = exhibition("새 기획전")
            mockMvc.get("/api/v1/promotions/banners") { with(me) }.andExpect { jsonPath("$.length()") { value(1) } }

            mockMvc.delete("/api-admin/v1/promotions/$id") { with(admin) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api/v1/promotions/banners") { with(me) }.andExpect { jsonPath("$.length()") { value(0) } }
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `prices and sold-out are always fresh even when the promotion is cached`() {
            val id = exhibition("가격 기획전")
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect { jsonPath("$.products[0].price") { value(5000) } }
            jdbc.update("update products set price = 4500 where id = ?", support.productId("모두 스티커 팩"))
            mockMvc.get("/api/v1/promotions/$id") { with(me) }.andExpect { jsonPath("$.products[0].price") { value(4500) } }
        }

        /** Redis 에는 JSON 으로 들어간다. 코틀린 data class·날짜·enum 이 그대로 돌아오는지(RedisCacheManager 와 같은 직렬화기). */
        @Test
        fun `redis json serialization round-trips banners and snapshots`() {
            val mapper = CacheConfig.cacheObjectMapper(Jackson2ObjectMapperBuilder.json().build())
            val banners =
                listOf(
                    PromotionBannerResult(
                        1,
                        PromotionType.EVENT,
                        "출석",
                        null,
                        null,
                        "#16A34A",
                        LocalDate.of(2026, 9, 25),
                        LocalDate.of(2026, 10, 24),
                    ),
                )
            val listType = mapper.typeFactory.constructCollectionType(List::class.java, PromotionBannerResult::class.java)
            val bannerSerializer = Jackson2JsonRedisSerializer<Any>(mapper, listType)
            assertEquals(banners, bannerSerializer.deserialize(bannerSerializer.serialize(banners)))

            val snapshot =
                PromotionSnapshot(
                    2,
                    PromotionType.EVENT,
                    EventKind.COUPON,
                    "쿠폰 팩",
                    "부제",
                    null,
                    null,
                    "#0EA5E9",
                    LocalDate.of(2026, 9, 25),
                    LocalDate.of(2026, 10, 24),
                    true,
                    listOf(3L, 1L),
                    listOf(7L),
                    null,
                )
            val snapshotSerializer =
                Jackson2JsonRedisSerializer<Any>(mapper, mapper.typeFactory.constructType(PromotionSnapshot::class.java))
            val json = String(snapshotSerializer.serialize(snapshot))
            assertEquals(snapshot, snapshotSerializer.deserialize(json.toByteArray()))
            assertTrue(json.contains("\"startDate\":\"2026-09-25\""), json)
        }
    }
