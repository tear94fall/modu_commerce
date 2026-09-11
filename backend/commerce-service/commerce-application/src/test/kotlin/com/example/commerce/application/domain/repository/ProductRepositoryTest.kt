package com.example.commerce.application.domain.repository

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.ro.ProductRoRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import javax.sql.DataSource

@SpringBootTest
class ProductRepositoryTest
    @Autowired
    constructor(
        private val productRwRepository: ProductRwRepository,
        private val productRoRepository: ProductRoRepository,
        @Qualifier("rwDataSource") private val rwDataSource: DataSource,
    ) {
        @BeforeEach
        fun cleanUp() {
            productRwRepository.deleteAll()
        }

        @Test
        fun `RW로 저장한 Product를 RO로 조회할 수 있다`() {
            val saved = productRwRepository.save(Product.create(name = "텀블러", description = "차가운", price = 24_000))

            val found = productRoRepository.findById(saved.id!!)

            assertNotNull(found)
            assertEquals(saved, found)
            assertTrue(saved.isPersisted())
        }

        @Test
        fun `저장 후 createdAt과 updatedAt이 DB에 기록된다`() {
            val saved = productRwRepository.save(Product.create(name = "텀블러", description = "차가운", price = 24_000))

            val row =
                rwDataSource.connection.use { c ->
                    c.createStatement().executeQuery("select created_at, updated_at from products where id = ${saved.id}").let { rs ->
                        rs.next()
                        rs.getObject(1) to rs.getObject(2)
                    }
                }

            assertNotNull(row.first)
            assertNotNull(row.second)
        }

        @Test
        fun `RO 커스텀 리포지토리로 이름과 설명을 검색한다`() {
            productRwRepository.save(Product.create(name = "모두 키보드", description = "저소음", price = 1))
            productRwRepository.save(Product.create(name = "모두 백팩", description = "노트북 수납", price = 2))
            productRwRepository.save(Product.create(name = "모두 텀블러", description = "보온", price = 3))

            assertEquals(listOf("모두 키보드"), productRoRepository.search("키보드").map { it.name })
            assertEquals(listOf("모두 백팩"), productRoRepository.search("노트북").map { it.name })
            assertEquals(3, productRoRepository.findAllByOrderByIdAsc().size)
        }
    }
