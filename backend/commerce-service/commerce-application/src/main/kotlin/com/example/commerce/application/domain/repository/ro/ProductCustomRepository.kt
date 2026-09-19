package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductSort
import com.example.commerce.application.domain.entity.ProductStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductCustomRepository {
    /** 이름 또는 설명에 keyword 가 들어간 상품을 id 순으로 */
    fun search(keyword: String): List<Product>

    /** 백오피스 목록. 최신 등록(id 내림차순)부터. keyword 가 비면 전체. */
    fun searchPage(
        keyword: String?,
        pageable: Pageable,
    ): Page<Product> = searchAdminPage(keyword, null, null, pageable)

    /** 백오피스 목록(필터 포함). */
    fun searchAdminPage(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        pageable: Pageable,
    ): Page<Product>

    /** 앱 목록. SELLING 만. [categoryIds] 가 null 이면 전체(빈 목록이면 결과 없음). */
    fun searchAppPage(
        categoryIds: Collection<Long>?,
        keyword: String?,
        sort: ProductSort,
        pageable: Pageable,
    ): Page<Product>
}
