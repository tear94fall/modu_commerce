package com.example.commerce.application.usecase.command

data class WriteReviewCommand(
    val orderItemId: Long,
    val rating: Int,
    val content: String,
)

data class EditReviewCommand(
    val rating: Int,
    val content: String,
)
