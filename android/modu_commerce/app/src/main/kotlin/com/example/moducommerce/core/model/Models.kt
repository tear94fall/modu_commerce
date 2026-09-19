package com.example.moducommerce.core.model

/** auth-service userinfo. 웹(마이 탭)에 브리지로 넘긴다. */
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val picture: String = "",
)
