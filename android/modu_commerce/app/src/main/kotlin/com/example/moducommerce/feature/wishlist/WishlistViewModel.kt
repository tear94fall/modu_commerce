package com.example.moducommerce.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.R
import com.example.moducommerce.core.session.WishStore
import com.example.moducommerce.data.repository.CatalogRepository
import com.example.moducommerce.feature.product.ProductListState
import com.example.moducommerce.feature.product.ProductPager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** 찜 탭. 찜을 풀면 목록에서 바로 빠진다. 탭에 들어올 때마다 다시 받는다. */
@HiltViewModel
class WishlistViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    wishStore: WishStore,
) : ViewModel() {

    private val pager = ProductPager(viewModelScope, wishStore, R.string.detail_wish_failed, removeUnwished = true) { page ->
        catalogRepository.wishlist(page = page)
    }
    val state: StateFlow<ProductListState> = pager.state

    fun load() = pager.loadFirst()

    fun loadMore() = pager.loadMore()

    fun toggleWish(productId: Long) = pager.toggleWish(productId)

    fun consumeMessage() = pager.consumeMessage()
}
