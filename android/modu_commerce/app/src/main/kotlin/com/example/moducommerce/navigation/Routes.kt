package com.example.moducommerce.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login?expired={expired}"
    const val WEB = "web"

    const val ARG_EXPIRED = "expired"

    fun login(expired: Boolean = false) = "login?expired=$expired"
}
