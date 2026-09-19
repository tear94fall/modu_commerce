package com.example.moducommerce.navigation

import android.net.Uri

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login?expired={expired}"
    const val MAIN = "main"
    const val SEARCH = "search"
    const val PRODUCTS = "products?categoryId={categoryId}&title={title}"
    const val PRODUCT = "product/{id}"

    const val ARG_ID = "id"
    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_TITLE = "title"
    const val ARG_EXPIRED = "expired"

    fun login(expired: Boolean = false) = "login?expired=$expired"

    fun product(id: Long) = "product/$id"

    fun products(categoryId: Long?, title: String) = "products?categoryId=${categoryId ?: -1}&title=${Uri.encode(title)}"
}
