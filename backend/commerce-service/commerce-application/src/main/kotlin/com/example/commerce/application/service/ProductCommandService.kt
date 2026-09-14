package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
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
) {
    fun create(command: ProductCommand): Product =
        productRwRepository.save(
            Product.create(name = command.name, description = command.description, price = command.price, imageUrl = command.imageUrl),
        )

    fun update(
        id: Long,
        command: ProductCommand,
    ): Product = findActive(id).apply { update(command.name, command.description, command.price, command.imageUrl) }

    fun delete(id: Long) {
        findActive(id).delete()
    }

    /** @SQLRestriction 이 걸려 있지만, 같은 트랜잭션에서 이미 로드된 엔티티도 있으니 삭제 여부를 한 번 더 본다. */
    private fun findActive(id: Long): Product =
        productRwRepository.findById(id).orElse(null)?.takeUnless { it.isDeleted() }
            ?: run {
                logger.error { "Product not found: $id" }
                throw EntityNotFoundException("id: $id 에 해당하는 상품이 없습니다.")
            }
}
