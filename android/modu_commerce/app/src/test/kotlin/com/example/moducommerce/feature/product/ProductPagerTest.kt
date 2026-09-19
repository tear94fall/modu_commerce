package com.example.moducommerce.feature.product

import com.example.moducommerce.core.model.Page
import com.example.moducommerce.core.session.WishStore
import com.example.moducommerce.testing.FakeCatalogRepository
import com.example.moducommerce.testing.product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductPagerTest {

    private val repository = FakeCatalogRepository()
    private val wishStore = WishStore(repository)

    private fun pager(scope: CoroutineScope, removeUnwished: Boolean = false) =
        ProductPager(scope, wishStore, wishFailedRes = 1, removeUnwished = removeUnwished) { page -> repository.products(page = page) }

    @Test
    fun `첫 장을 받고 마지막 줄이 보이면 다음 장을 이어 붙인다`() = runTest {
        repository.pages[0] = Page(listOf(product(1), product(2)), 3, 2, 0)
        repository.pages[1] = Page(listOf(product(3)), 3, 2, 1)
        val pager = pager(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

        pager.loadFirst()
        assertEquals(listOf(1L, 2L), pager.state.value.items.map { it.id })
        assertTrue(pager.state.value.hasNext)

        pager.loadMore()
        assertEquals(listOf(1L, 2L, 3L), pager.state.value.items.map { it.id })
        assertFalse(pager.state.value.hasNext)

        pager.loadMore() // 더 없으면 아무것도 안 한다
        assertEquals(3, pager.state.value.items.size)
    }

    @Test
    fun `찜은 먼저 화면을 바꾸고 실패하면 되돌리며 안내한다`() = runTest {
        repository.pages[0] = Page(listOf(product(1)), 1, 1, 0)
        val pager = pager(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        pager.loadFirst()

        pager.toggleWish(1)
        assertTrue(pager.state.value.items.single().wished)
        assertEquals(listOf(1L to true), repository.wishCalls)

        repository.wishResult = Result.failure(RuntimeException("500"))
        pager.toggleWish(1)
        assertTrue("실패하면 원래대로", pager.state.value.items.single().wished)
        assertEquals(1, pager.state.value.messageRes)
        pager.consumeMessage()
        assertEquals(null, pager.state.value.messageRes)
    }

    @Test
    fun `다른 화면에서 바뀐 찜은 방송으로 반영되고 찜 탭은 풀린 상품을 지운다`() = runTest {
        repository.pages[0] = Page(listOf(product(1, wished = true), product(2, wished = true)), 2, 1, 0)
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val list = pager(scope)
        val wishlist = pager(scope, removeUnwished = true)
        list.loadFirst()
        wishlist.loadFirst()

        wishStore.set(1, false)

        assertFalse(list.state.value.items.first { it.id == 1L }.wished)
        assertEquals(listOf(2L), wishlist.state.value.items.map { it.id })
    }

    @Test
    fun `실패하면 error 이고 다시 받으면 지워진다`() = runTest {
        val pager = pager(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        repository.fail = true
        pager.loadFirst()
        assertTrue(pager.state.value.error)
        repository.fail = false
        repository.pages[0] = Page(listOf(product(1)), 1, 1, 0)
        pager.loadFirst()
        assertFalse(pager.state.value.error)
        assertEquals(1, pager.state.value.items.size)
    }
}
