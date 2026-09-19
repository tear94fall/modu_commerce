package com.example.moducommerce.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.model.Category
import com.example.moducommerce.core.model.ProductSort
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.core.session.WishStore
import com.example.moducommerce.data.repository.CatalogRepository
import com.example.moducommerce.feature.product.ProductListState
import com.example.moducommerce.feature.product.ProductPager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeSections(
    val categories: List<Category> = emptyList(),
    val newest: List<ProductSummary> = emptyList(),
    val popular: List<ProductSummary> = emptyList(),
)

/** 홈: 카테고리 칩, 새 상품·인기 상품 가로줄, 그 아래 전체 상품 격자(최신순 페이징). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val wishStore: WishStore,
) : ViewModel() {

    private val _sections = MutableStateFlow(HomeSections())
    val sections: StateFlow<HomeSections> = _sections.asStateFlow()

    private val pager = ProductPager(viewModelScope, wishStore, R.string.detail_wish_failed) { page ->
        catalogRepository.products(sort = ProductSort.LATEST, page = page)
    }
    val state: StateFlow<ProductListState> = pager.state

    init {
        load()
        viewModelScope.launch {
            wishStore.changes.collect { change ->
                _sections.update { s -> s.copy(newest = s.newest.patch(change.productId, change.wished), popular = s.popular.patch(change.productId, change.wished)) }
            }
        }
    }

    fun load() {
        pager.loadFirst()
        viewModelScope.launch {
            catalogRepository.categories().onSuccess { list -> _sections.update { it.copy(categories = list) } }
        }
        viewModelScope.launch {
            catalogRepository.products(sort = ProductSort.LATEST, page = 0, size = ROW_SIZE)
                .onSuccess { page -> _sections.update { it.copy(newest = page.content) } }
        }
        viewModelScope.launch {
            catalogRepository.products(sort = ProductSort.POPULAR, page = 0, size = ROW_SIZE)
                .onSuccess { page -> _sections.update { it.copy(popular = page.content) } }
        }
    }

    fun loadMore() = pager.loadMore()

    /** 가로줄과 격자 어디서 눌러도 같은 규칙. 격자에 없는 상품(가로줄 전용)은 여기서 직접 처리한다. */
    fun toggleWish(productId: Long) {
        if (state.value.items.any { it.id == productId }) {
            pager.toggleWish(productId)
            return
        }
        val current = (_sections.value.newest + _sections.value.popular).firstOrNull { it.id == productId } ?: return
        val next = !current.wished
        _sections.update { s -> s.copy(newest = s.newest.patch(productId, next), popular = s.popular.patch(productId, next)) }
        viewModelScope.launch {
            wishStore.set(productId, next).onFailure {
                _sections.update { s -> s.copy(newest = s.newest.patch(productId, !next), popular = s.popular.patch(productId, !next)) }
            }
        }
    }

    fun consumeMessage() = pager.consumeMessage()

    private fun List<ProductSummary>.patch(productId: Long, wished: Boolean) = map { if (it.id == productId) it.copy(wished = wished) else it }

    companion object {
        const val ROW_SIZE = 10
    }
}
