/**
 * SmartRAG v2 - Search Result
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag.api

data class SearchResult(
    val items: List<SearchResultItem>,
    val totalCount: Int,
    val searchTimeMs: Long
)

data class SearchResultItem(
    val id: Long,
    val text: String,
    val score: Float,
    val metadata: Map<String, Any> = emptyMap()
)
