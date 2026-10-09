package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.domain.repository.rw.ProductSkuRwRepository
import com.example.commerce.application.usecase.command.ProductCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 쓰기는 전부 master(RW). 등록·수정 결과는 방금 쓴 RW 엔티티로 돌려준다 —
 * 레플리카에서 다시 읽으면 복제 지연 동안 옛값이 나올 수 있다.
 */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class ProductCommandService(
    private val productRwRepository: ProductRwRepository,
    private val productSkuRwRepository: ProductSkuRwRepository,
    private val categoryCommandService: CategoryCommandService,
) {
    fun create(command: ProductCommand): Product {
        val product = Product.create(name = command.name, description = command.description, price = command.price)
        apply(product, command, emptyMap())
        return productRwRepository.saveAndFlush(product)
    }

    fun update(
        id: Long,
        command: ProductCommand,
    ): Product =
        findActive(id).also {
            apply(it, command, lockStocks(it, command))
            // 새 옵션 값·SKU 의 id 는 flush 뒤에야 생긴다. 응답 매핑이 같은 트랜잭션 안에서 돌기 때문에 여기서 밀어 넣는다.
            productRwRepository.flush()
        }

    fun delete(id: Long) {
        findActive(id).delete()
    }

    /** @SQLRestriction 이 걸려 있지만, 같은 트랜잭션에서 이미 로드된 엔티티도 있으니 삭제 여부를 한 번 더 본다. */
    fun findActive(id: Long): Product =
        productRwRepository.findById(id).orElse(null)?.takeUnless { it.isDeleted() }
            ?: run {
                logger.error { "Product not found: $id" }
                throw EntityNotFoundException("id: $id 에 해당하는 상품이 없습니다.")
            }

    /**
     * 재고를 증감(baseStock)으로 바꿀 기존 SKU 를 id 순으로 잠그고 지금 재고를 읽는다. 주문 생성·취소도 SKU 를 id 순으로 잠그므로
     * 교착 없이 줄을 선다. 잠근 행은 커밋까지 다른 주문이 못 바꾸므로 "지금 재고 + 증감" 이 그대로 저장된다.
     */
    private fun lockStocks(
        product: Product,
        command: ProductCommand,
    ): Map<Long, Int> =
        product
            .skusWithStockDelta(command.skus)
            .mapNotNull { it.id }
            .sorted()
            .mapNotNull { id -> productSkuRwRepository.lockStock(id)?.let { id to it } }
            .toMap()

    private fun apply(
        product: Product,
        command: ProductCommand,
        currentStocks: Map<Long, Int>,
    ) {
        val category = command.categoryId?.let { categoryCommandService.findActive(it) }
        product.update(command.name, command.description, command.price, product.imageUrl)
        product.updateCatalog(category = category, listPrice = command.listPrice, detail = command.detail, status = command.status)
        product.replaceImages(command.images)
        product.replaceOptions(command.optionGroups, command.skus, currentStocks)
    }
}
