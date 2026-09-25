package com.example.commerce.application.usecase.wishlist

import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.service.WishlistCommandService
import com.example.commerce.application.service.WishlistQueryService
import com.example.commerce.application.usecase.result.PageResult
import com.example.commerce.application.usecase.result.ProductSummaryResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetWishlistUseCase(
    private val wishlistQueryService: WishlistQueryService,
) {
    /** 숨긴 상품은 찜 목록에서도 감춘다(삭제된 상품은 @SQLRestriction 이 거른다). */
    @Transactional(transactionManager = "roTransactionManager", readOnly = true)
    fun execute(
        userId: String,
        page: Int,
        size: Int,
    ): PageResult<ProductSummaryResult> {
        val wishes = wishlistQueryService.findPage(userId, page, size)
        return PageResult(
            content =
                wishes.content.mapNotNull { wish ->
                    wish.product.takeIf { it.status == ProductStatus.SELLING }?.let { ProductSummaryResult.from(it, wished = true) }
                },
            totalElements = wishes.totalElements,
            totalPages = wishes.totalPages,
            number = wishes.number,
            size = wishes.size,
        )
    }
}

@Component
class AddWishUseCase(
    private val wishlistCommandService: WishlistCommandService,
) {
    fun execute(
        userId: String,
        productId: Long,
    ) = wishlistCommandService.add(userId, productId)
}

@Component
class RemoveWishUseCase(
    private val wishlistCommandService: WishlistCommandService,
) {
    fun execute(
        userId: String,
        productId: Long,
    ) = wishlistCommandService.remove(userId, productId)
}
