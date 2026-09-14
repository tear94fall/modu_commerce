package com.example.commerce.application.service

import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.usecase.CreateProductUseCase
import com.example.commerce.application.usecase.DeleteProductUseCase
import com.example.commerce.application.usecase.UpdateProductUseCase
import com.example.commerce.application.usecase.command.ProductCommand
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
        private val umbrella = ProductCommand(name = "모두 우산", description = "자동 우산", price = 15_000, imageUrl = null)

        @BeforeEach
        fun cleanUp() {
            productRwRepository.deleteAll()
        }

        @Test
        fun `등록한 상품은 RO 로 조회된다`() {
            val created = createProductUseCase.execute(umbrella)

            assertEquals("모두 우산", created.name)
            assertEquals("자동 우산", productQueryService.findProduct(created.id).description)
        }

        @Test
        fun `수정하면 모든 필드가 바뀐다`() {
            val created = createProductUseCase.execute(umbrella)

            val updated = updateProductUseCase.execute(created.id, ProductCommand("모두 장우산", "튼튼한", 21_000, "https://img/u.png"))

            assertEquals(created.id, updated.id)
            val found = productQueryService.findProduct(created.id)
            assertEquals(listOf("모두 장우산", "튼튼한", "https://img/u.png"), listOf(found.name, found.description, found.imageUrl))
            assertEquals(21_000L, found.price)
        }

        @Test
        fun `삭제하면 앱 조회와 admin 목록에서 사라지고 다시 수정 삭제할 수 없다`() {
            val created = createProductUseCase.execute(umbrella)

            deleteProductUseCase.execute(created.id)

            assertEquals(emptyList<Long>(), productQueryService.findProducts(null).map { it.id })
            assertEquals(0L, productQueryService.searchAdminProducts(null, 0, 15).totalElements)
            assertThrows(EntityNotFoundException::class.java) { productQueryService.findProduct(created.id) }
            assertThrows(EntityNotFoundException::class.java) { deleteProductUseCase.execute(created.id) }
            assertThrows(EntityNotFoundException::class.java) { updateProductUseCase.execute(created.id, umbrella) }
        }

        @Test
        fun `없는 상품은 수정 삭제할 수 없다`() {
            assertThrows(EntityNotFoundException::class.java) { updateProductUseCase.execute(999_999L, umbrella) }
            assertThrows(EntityNotFoundException::class.java) { deleteProductUseCase.execute(999_999L) }
        }
    }
