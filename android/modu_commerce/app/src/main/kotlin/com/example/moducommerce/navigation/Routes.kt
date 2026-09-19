package com.example.moducommerce.navigation

import android.net.Uri

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login?expired={expired}"
    const val MAIN = "main"
    const val SEARCH = "search"
    const val PRODUCTS = "products?categoryId={categoryId}&title={title}"
    const val PRODUCT = "product/{id}"
    const val CART = "cart"
    const val CHECKOUT = "checkout?cartItemIds={cartItemIds}&productId={productId}&skuId={skuId}&quantity={quantity}"
    const val ORDERS = "orders"
    const val ORDER = "order/{id}"
    const val ADDRESSES = "addresses"

    const val ARG_ID = "id"
    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_TITLE = "title"
    const val ARG_EXPIRED = "expired"
    const val ARG_CART_ITEM_IDS = "cartItemIds"
    const val ARG_PRODUCT_ID = "productId"
    const val ARG_SKU_ID = "skuId"
    const val ARG_QUANTITY = "quantity"

    fun login(expired: Boolean = false) = "login?expired=$expired"

    fun product(id: Long) = "product/$id"

    fun products(categoryId: Long?, title: String) = "products?categoryId=${categoryId ?: -1}&title=${Uri.encode(title)}"

    fun checkoutFromCart(cartItemIds: List<Long>) = "checkout?cartItemIds=${cartItemIds.joinToString(",")}&productId=-1&skuId=-1&quantity=1"

    fun checkoutDirect(productId: Long, skuId: Long, quantity: Int) = "checkout?cartItemIds=&productId=$productId&skuId=$skuId&quantity=$quantity"

    fun order(id: Long) = "order/$id"
}
