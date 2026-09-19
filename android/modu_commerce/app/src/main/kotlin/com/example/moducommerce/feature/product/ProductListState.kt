package com.example.moducommerce.feature.product

import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.core.session.WishStore
import com.example.moducommerce.data.repository.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductListState(
    val items: List<ProductSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val hasNext: Boolean = false,
    val page: Int = 0,
    /** 스낵바 문구 리소스. 화면이 읽고 [ProductPager.consumeMessage] 로 지운다. */
    val messageRes: Int? = null,
)

/**
 * 페이지 목록 + 찜 토글의 공통 로직. 홈·카테고리·검색·찜 탭이 같은 규칙을 쓴다.
 * [request] 가 페이지 번호로 한 장을 받아 온다. 찜은 먼저 화면을 바꾸고 실패하면 되돌린다.
 */
class ProductPager(
    private val scope: CoroutineScope,
    private val wishStore: WishStore,
    private val wishFailedRes: Int,
    /** 찜을 풀면 목록에서 지운다(찜 탭). */
    private val removeUnwished: Boolean = false,
    private val request: suspend (page: Int) -> Result<Page<ProductSummary>>,
) {
    private val _state = MutableStateFlow(ProductListState())
    val state: StateFlow<ProductListState> = _state.asStateFlow()

    private var generation = 0

    init {
        scope.launch {
            wishStore.changes.collect { change -> applyWish(change.productId, change.wished) }
        }
    }

    fun loadFirst() {
        val gen = ++generation
        _state.update { it.copy(loading = true, error = false) }
        scope.launch {
            request(0).onSuccess { page ->
                if (gen != generation) return@launch
                _state.update { it.copy(items = page.content, loading = false, hasNext = page.hasNext, page = 0) }
            }.onFailure {
                if (gen != generation) return@launch
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasNext) return
        val gen = generation
        _state.update { it.copy(loadingMore = true) }
        scope.launch {
            request(current.page + 1).onSuccess { page ->
                if (gen != generation) return@launch
                _state.update { it.copy(items = it.items + page.content, loadingMore = false, hasNext = page.hasNext, page = page.number) }
            }.onFailure {
                if (gen != generation) return@launch
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun toggleWish(productId: Long) {
        val product = _state.value.items.firstOrNull { it.id == productId } ?: return
        val next = !product.wished
        applyWish(productId, next)
        scope.launch {
            wishStore.set(productId, next).onFailure {
                // 되돌린다. 방송은 서버가 바뀌었을 때만 나가므로 여기서는 이 목록만 고친다.
                _state.update { s -> s.copy(items = s.items.map { if (it.id == productId) it.copy(wished = !next) else it }, messageRes = wishFailedRes) }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(messageRes = null) }
    }

    private fun applyWish(productId: Long, wished: Boolean) {
        _state.update { s ->
            if (removeUnwished && !wished) {
                s.copy(items = s.items.filterNot { it.id == productId })
            } else {
                s.copy(items = s.items.map { if (it.id == productId) it.copy(wished = wished) else it })
            }
        }
    }
}
