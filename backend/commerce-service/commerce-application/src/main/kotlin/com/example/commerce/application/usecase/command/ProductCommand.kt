package com.example.commerce.application.usecase.command

/** 등록·수정 입력. 형식 검증(길이·범위·URL)은 api 모듈의 요청 DTO 가 끝낸 값이다. */
data class ProductCommand(
    val name: String,
    val description: String,
    val price: Long,
    val imageUrl: String?,
)
