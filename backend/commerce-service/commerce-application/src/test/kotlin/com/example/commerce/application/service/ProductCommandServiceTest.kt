package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.OptionGroupSpec
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.SkuSpec
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.usecase.command.ProductCommand
import com.example.commerce.application.usecase.product.CreateProductUseCase
import com.example.commerce.application.usecase.product.DeleteProductUseCase
import com.example.commerce.application.usecase.product.UpdateProductUseCase
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductCommandServiceTest
    @Autowired
    constructor(
        private val createProductUseCase: CreateProductUseCase,
        private val updateProductUseCase: UpdateProductUseCase,
        private val deleteProductUseCase: DeleteProductUseCase,
        private val productQueryService: ProductQueryService,
        private val productRwRepository: ProductRwRepository,
    ) {
        private fun command(
            name: String = "모두 우산",
            description: String = "자동 우산",
            price: Long = 15_000,
            images: List<String> = emptyList(),
            listPrice: Long? = null,
            groups: List<OptionGroupSpec> = emptyList(),
            skus: List<SkuSpec> = listOf(SkuSpec(emptyMap(), 0, 3)),
        ) = ProductCommand(
            name = name,
            description = description,
            detail = null,
            price = price,
            listPrice = listPrice,
            categoryId = null,
            status = ProductStatus.SELLING,
            images = images,
            optionGroups = groups,
            skus = skus,
        )

        @BeforeEach
        fun cleanUp() {
            productRwRepository.deleteAll()
        }

        @Test
        fun `등록한 상품은 RO 로 조회된다`() {
            val created = createProductUseCase.execute(command())

            assertEquals("모두 우산", created.name)
            assertEquals(1, created.skus.size)
            assertEquals("자동 우산", productQueryService.findProduct(created.id).description)
        }

        @Test
        fun `수정하면 모든 필드가 바뀐다`() {
            val created = createProductUseCase.execute(command())

            val updated =
                updateProductUseCase.execute(
                    created.id,
                    command("모두 장우산", "튼튼한", 21_000, images = listOf("https://img/u.png"), listPrice = 25_000),
                )

            assertEquals(created.id, updated.id)
            assertEquals(16, updated.discountRate)
            val found = productQueryService.findProduct(created.id)
            assertEquals(listOf("모두 장우산", "튼튼한", "https://img/u.png"), listOf(found.name, found.description, found.imageUrl))
            assertEquals(21_000L, found.price)
        }

        @Test
        fun `옵션 조합 규칙 위반은 IllegalArgumentException 이고 저장되지 않는다`() {
            val bad = command(groups = listOf(OptionGroupSpec("색상", listOf("블랙"))), skus = listOf(SkuSpec(mapOf("색상" to "레드"))))
            assertThrows(IllegalArgumentException::class.java) { createProductUseCase.execute(bad) }
            assertEquals(0L, productRwRepository.count())
        }

        @Test
        fun `삭제하면 앱 조회와 admin 목록에서 사라지고 다시 수정 삭제할 수 없다`() {
            val created = createProductUseCase.execute(command())

            deleteProductUseCase.execute(created.id)

            assertEquals(emptyList<Long>(), productQueryService.findProducts(null).map { it.id })
            assertEquals(0L, productQueryService.searchAdminProducts(null, 0, 15).totalElements)
            assertThrows(EntityNotFoundException::class.java) { productQueryService.findProduct(created.id) }
            assertThrows(EntityNotFoundException::class.java) { deleteProductUseCase.execute(created.id) }
            assertThrows(EntityNotFoundException::class.java) { updateProductUseCase.execute(created.id, command()) }
        }

        @Test
        fun `없는 상품은 수정 삭제할 수 없다`() {
            assertThrows(EntityNotFoundException::class.java) { updateProductUseCase.execute(999_999L, command()) }
            assertThrows(EntityNotFoundException::class.java) { deleteProductUseCase.execute(999_999L) }
        }
    }
