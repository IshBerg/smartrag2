/**
 * SmartRAG v2 - Search Parameters
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag.api

data class SearchParams(
    val limit: Int = 10,
    val minScore: Float = 0.0f,
    val includeMetadata: Boolean = true
) {
    companion object {
        fun default() = SearchParams()
    }
}
