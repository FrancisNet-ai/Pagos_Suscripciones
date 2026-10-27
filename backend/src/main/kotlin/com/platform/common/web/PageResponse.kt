package com.platform.common.web

import org.springframework.data.domain.Page

data class PageResponse<T : Any>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        const val MAX_SIZE = 100

        fun <E : Any, T : Any> of(page: Page<E>, mapper: (E) -> T) =
            PageResponse(page.content.map(mapper), page.number, page.size, page.totalElements, page.totalPages)
    }
}
