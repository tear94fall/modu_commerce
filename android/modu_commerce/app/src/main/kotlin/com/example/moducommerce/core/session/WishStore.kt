package com.example.moducommerce.core.session

import com.example.moducommerce.data.repository.CatalogRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 찜 상태 변경 한 곳. 여러 화면(목록·상세·찜 탭)이 같은 상품을 보여 주므로 결과를 모두에게 방송한다. */
@Singleton
class WishStore @Inject constructor(
    private val catalogRepository: CatalogRepository,
) {
    data class WishChange(val productId: Long, val wished: Boolean)

    private val _changes = MutableSharedFlow<WishChange>(replay = 0, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes: SharedFlow<WishChange> = _changes.asSharedFlow()

    suspend fun set(productId: Long, wished: Boolean): Result<Unit> =
        catalogRepository.setWished(productId, wished).onSuccess { _changes.tryEmit(WishChange(productId, wished)) }
}
