package com.example.moducommerce.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.model.ProductSort
import com.example.moducommerce.core.session.WishStore
import com.example.moducommerce.data.repository.CatalogRepository
import com.example.moducommerce.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** 카테고리 상품 목록(`products?categoryId=&title=`). 정렬을 바꾸면 첫 페이지부터 다시 받는다. */
@HiltViewModel
class ProductListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    wishStore: WishStore,
) : ViewModel() {

    val categoryId: Long? = savedStateHandle.get<Long>(Routes.ARG_CATEGORY_ID)?.takeIf { it >= 0 }
    val title: String = savedStateHandle.get<String>(Routes.ARG_TITLE).orEmpty()

    private val _sort = MutableStateFlow(ProductSort.LATEST)
    val sort: StateFlow<ProductSort> = _sort.asStateFlow()

    private val pager = ProductPager(viewModelScope, wishStore, R.string.detail_wish_failed) { page ->
        catalogRepository.products(categoryId = categoryId, sort = _sort.value, page = page)
    }
    val state: StateFlow<ProductListState> = pager.state

    init {
        pager.loadFirst()
    }

    fun onSortChange(sort: ProductSort) {
        if (_sort.value == sort) return
        _sort.value = sort
        pager.loadFirst()
    }

    fun retry() = pager.loadFirst()

    fun loadMore() = pager.loadMore()

    fun toggleWish(productId: Long) = pager.toggleWish(productId)

    fun consumeMessage() = pager.consumeMessage()
}
