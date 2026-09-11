package com.example.commerce.application.seed

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 테스트용 상품 4개. 테이블이 비어 있을 때만 넣는다. */
@Component
class ProductSeeder(
    private val productRwRepository: ProductRwRepository,
) : ApplicationRunner {
    @Transactional(transactionManager = "rwTransactionManager")
    override fun run(args: ApplicationArguments) {
        if (productRwRepository.count() > 0) return
        productRwRepository.saveAll(sampleProducts())
        logger.info { "테스트 상품 ${SAMPLE_COUNT}개를 넣었습니다." }
    }

    companion object {
        const val SAMPLE_COUNT = 4

        fun sampleProducts(): List<Product> =
            listOf(
                Product.create(name = "모두 무선 이어폰", description = "가볍고 통화가 또렷한 무선 이어폰", price = 89_000),
                Product.create(name = "모두 텀블러 500ml", description = "하루 종일 차가운 스테인리스 텀블러", price = 24_000),
                Product.create(name = "모두 데일리 백팩", description = "노트북 15인치가 들어가는 생활 방수 백팩", price = 59_000),
                Product.create(name = "모두 기계식 키보드", description = "저소음 적축, 한글 각인 텐키리스", price = 129_000),
            )
    }
}
