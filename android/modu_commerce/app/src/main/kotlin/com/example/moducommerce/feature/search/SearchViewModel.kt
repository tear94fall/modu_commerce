package com.example.moducommerce.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.model.ProductSort
import com.example.moducommerce.core.session.WishStore
import com.example.moducommerce.data.repository.CatalogRepository
import com.example.moducommerce.feature.product.ProductListState
import com.example.moducommerce.feature.product.ProductPager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** 검색. 검색어를 제출해야 서버를 부른다(입력마다 부르지 않는다). */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    wishStore: WishStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 마지막으로 제출한 검색어. 비어 있으면 아직 검색 전이다. */
    private val _submitted = MutableStateFlow("")
    val submitted: StateFlow<String> = _submitted.asStateFlow()

    private val _sort = MutableStateFlow(ProductSort.LATEST)
    val sort: StateFlow<ProductSort> = _sort.asStateFlow()

    private val pager = ProductPager(viewModelScope, wishStore, R.string.detail_wish_failed) { page ->
        val q = _submitted.value
        if (q.isEmpty()) Result.success(Page(emptyList(), 0, 0, 0)) else catalogRepository.products(query = q, sort = _sort.value, page = page)
    }
    val state: StateFlow<ProductListState> = pager.state

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun submit() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        _submitted.value = q
        pager.loadFirst()
    }

    fun onSortChange(sort: ProductSort) {
        if (_sort.value == sort) return
        _sort.value = sort
        if (_submitted.value.isNotEmpty()) pager.loadFirst()
    }

    fun retry() = pager.loadFirst()

    fun loadMore() = pager.loadMore()

    fun toggleWish(productId: Long) = pager.toggleWish(productId)

    fun consumeMessage() = pager.consumeMessage()
}
