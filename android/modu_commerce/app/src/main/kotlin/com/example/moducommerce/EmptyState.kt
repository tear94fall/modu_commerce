package com.example.moducommerce

/** 상품 목록이 비었을 때 보여 줄 안내의 종류. 문구는 화면이 strings.xml 에서 고른다. */
sealed class EmptyState {
    object NoProducts : EmptyState()
    data class NoResults(val query: String) : EmptyState()

    companion object {
        fun of(query: String?): EmptyState =
            query?.trim()?.takeIf { it.isNotEmpty() }?.let { NoResults(it) } ?: NoProducts
    }
}
