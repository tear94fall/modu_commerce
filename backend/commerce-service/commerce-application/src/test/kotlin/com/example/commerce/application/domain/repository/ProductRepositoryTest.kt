package com.example.commerce.application.domain.repository

import com.example.commerce.application.domain.entity.Category
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductSort
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.repository.ro.ProductRoRepository
import com.example.commerce.application.domain.repository.rw.CategoryRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.seed.ProductSeeder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

@SpringBootTest
class ProductRepositoryTest
    @Autowired
    constructor(
        private val productRwRepository: ProductRwRepository,
        private val productRoRepository: ProductRoRepository,
        private val categoryRwRepository: CategoryRwRepository,
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

        @Test
        fun `삭제한 상품은 RO 조회 검색 단건에서 빠진다`() {
            val kept = productRwRepository.save(Product.create(name = "모두 키보드", description = "저소음", price = 1))
            val gone = productRwRepository.save(Product.create(name = "모두 키캡", description = "저소음", price = 2))
            gone.delete()
            productRwRepository.save(gone)

            assertEquals(listOf(kept.id), productRoRepository.findAllByOrderByIdAsc().map { it.id })
            assertEquals(listOf("모두 키보드"), productRoRepository.search("저소음").map { it.name })
            assertEquals(null, productRoRepository.findById(gone.id!!))
        }

        @Test
        fun `countIncludingDeleted는 삭제한 행도 센다`() {
            val gone = productRwRepository.save(Product.create(name = "모두 키캡", description = "", price = 2))
            gone.delete()
            productRwRepository.save(gone)

            assertEquals(0L, productRwRepository.count())
            assertTrue(productRwRepository.countIncludingDeleted() >= 1L)
        }

        @Test
        fun `searchPage는 최신 등록순으로 자르고 삭제한 상품은 세지 않는다`() {
            val a = productRwRepository.save(Product.create(name = "모두 A", description = "", price = 1))
            val b = productRwRepository.save(Product.create(name = "모두 B", description = "", price = 2))
            val c = productRwRepository.save(Product.create(name = "모두 C", description = "저소음", price = 3))
            val gone = productRwRepository.save(Product.create(name = "모두 D", description = "", price = 4))
            gone.delete()
            productRwRepository.save(gone)

            val first = productRoRepository.searchPage(null, PageRequest.of(0, 2))
            assertEquals(listOf(c.id, b.id), first.content.map { it.id })
            assertEquals(3L, first.totalElements)
            assertEquals(2, first.totalPages)

            assertEquals(listOf(a.id), productRoRepository.searchPage("  ", PageRequest.of(1, 2)).content.map { it.id })
            assertEquals(listOf("모두 C"), productRoRepository.searchPage("저소음", PageRequest.of(0, 10)).content.map { it.name })
        }

        // ---------- 카탈로그 ----------

        private fun catalog(): Map<String, Category> {
            categoryRwRepository.deleteAll()
            val categories = categoryRwRepository.saveAll(ProductSeeder.sampleCategories()).associateBy { it.name }
            productRwRepository.saveAll(ProductSeeder.sampleProducts(categories))
            return categories
        }

        @Test
        fun `앱 목록은 판매중만, 하위 카테고리 id 로 거르고 정렬한다`() {
            val categories = catalog()
            val page = PageRequest.of(0, 50)

            val all = productRoRepository.searchAppPage(null, null, ProductSort.LATEST, page)
            assertEquals(ProductSeeder.SAMPLE_COUNT.toLong(), all.totalElements)
            assertEquals("모두 스티커 팩", all.content.first().name)

            val childIds = listOf(categories.getValue("의류").id!!)
            assertEquals(
                listOf(
                    "모두 후드 집업",
                    "모두 베이직 티셔츠",
                ),
                productRoRepository.searchAppPage(childIds, null, ProductSort.LATEST, page).content.map {
                    it.name
                },
            )

            val cheapest = productRoRepository.searchAppPage(null, null, ProductSort.PRICE_ASC, page).content.first()
            assertEquals("모두 스티커 팩", cheapest.name)
            val priciest = productRoRepository.searchAppPage(null, null, ProductSort.PRICE_DESC, page).content.first()
            assertEquals("모두 기계식 키보드", priciest.name)

            assertEquals(1, productRoRepository.searchAppPage(null, "텀블러", ProductSort.LATEST, page).totalElements)
            assertEquals(0, productRoRepository.searchAppPage(emptyList(), null, ProductSort.LATEST, page).totalElements)
        }

        @Test
        fun `숨긴 상품은 앱 목록에서 빠지고 어드민 목록에는 남는다`() {
            catalog()
            val hidden = productRoRepository.search("버킷햇").single()
            val rw = productRwRepository.findById(hidden.id!!).get()
            rw.updateCatalog(rw.category, rw.listPrice, rw.detail, ProductStatus.HIDDEN)
            productRwRepository.save(rw)

            val page = PageRequest.of(0, 50)
            assertTrue(productRoRepository.searchAppPage(null, "버킷햇", ProductSort.LATEST, page).content.isEmpty())
            assertEquals(1, productRoRepository.searchAdminPage("버킷햇", null, ProductStatus.HIDDEN, page).totalElements)
            assertEquals(ProductSeeder.SAMPLE_COUNT.toLong(), productRoRepository.searchAdminPage(null, null, null, page).totalElements)
        }

        @Test
        fun `인기순은 찜 수 내림차순`() {
            catalog()
            val mug = productRwRepository.findAll().first { it.name == "모두 머그컵 세트" }
            repeat(3) { mug.increaseWishCount() }
            productRwRepository.save(mug)

            val first = productRoRepository.searchAppPage(null, null, ProductSort.POPULAR, PageRequest.of(0, 1)).content.single()
            assertEquals("모두 머그컵 세트", first.name)
        }

        @Test
        @Transactional(transactionManager = "roTransactionManager", readOnly = true)
        fun `저장한 상품의 사진 옵션 SKU 를 RO 로 다시 읽는다`() {
            val categories = catalog()
            val tshirt = productRoRepository.search("베이직 티셔츠").single()
            assertEquals(2, tshirt.images.size)
            assertEquals(listOf("색상", "사이즈"), tshirt.optionGroups.map { it.name })
            assertEquals(6, tshirt.skus.size)
            assertEquals(listOf("패션", "의류"), tshirt.category!!.path().map { it.name })
            assertEquals(categories.getValue("의류").id, tshirt.category!!.id)
            assertTrue(tshirt.skus.any { it.optionLabel() == "네이비 / L" && it.isSoldOut() })
        }
    }
