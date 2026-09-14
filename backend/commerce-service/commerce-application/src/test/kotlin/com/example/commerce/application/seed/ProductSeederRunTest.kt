package com.example.commerce.application.seed

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductSeederRunTest
    @Autowired
    constructor(
        private val productSeeder: ProductSeeder,
        private val productRwRepository: ProductRwRepository,
    ) {
        /** 관리자가 상품을 전부 지웠다고 재기동 때 테스트 상품이 되살아나면 안 된다. */
        @Test
        fun `삭제된 상품만 남아 있으면 다시 넣지 않는다`() {
            productRwRepository.deleteAll()
            val gone = productRwRepository.save(Product.create(name = "지운 상품", description = "", price = 1))
            gone.delete()
            productRwRepository.save(gone)

            productSeeder.run(DefaultApplicationArguments())

            assertEquals(0L, productRwRepository.count())
        }
    }
