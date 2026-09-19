package com.example.commerce.application.domain.entity

/** 관리자가 정하는 판매 상태. 품절은 저장하지 않고 SKU 재고로 계산한다. */
enum class ProductStatus { SELLING, HIDDEN }
