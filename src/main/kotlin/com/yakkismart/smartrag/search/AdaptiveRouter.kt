/**
 * SmartRAG v2 - Adaptive Router
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Выбирает оптимальную стратегию поиска на основе типа запроса.
 */
package com.yakkismart.smartrag.search

import com.yakkismart.smartrag.api.SearchParams
import com.yakkismart.smartrag.api.SearchResult

/**
 * Адаптивный роутер стратегий.
 */
class AdaptiveRouter(
    private val textSearch: TextSearch,
    private val vectorSearch: VectorSearch,
    private val graphSearch: GraphSearch,
    private val hybridSearch: HybridSearchEngine
) {

    /**
     * Режимы поиска.
     */
    enum class SearchMode {
        FAST,       // Только FTS5 (~30ms)
        BALANCED,   // FTS5 + векторы (~80ms)
        DEEP        // Всё включено (~150ms)
    }

    /**
     * Выполняет поиск в выбранном режиме.
     */
    suspend fun search(
        query: String,
        params: SearchParams,
        mode: SearchMode
    ): Result<SearchResult> {
        return when (mode) {
            SearchMode.FAST -> textSearch.search(query, params)
            SearchMode.BALANCED -> {
                // FTS5 + векторы
                val textWeight = textSearch.isApplicable(query)
                val vectorWeight = vectorSearch.isApplicable(query)

                if (textWeight > vectorWeight * 1.5f) {
                    textSearch.search(query, params)
                } else {
                    hybridSearch.search(query, params)
                }
            }
            SearchMode.DEEP -> hybridSearch.search(query, params)
        }
    }

    /**
     * Автоматический выбор режима на основе запроса.
     */
    suspend fun searchAuto(
        query: String,
        params: SearchParams
    ): Result<SearchResult> {
        val mode = selectMode(query)
        return search(query, params, mode)
    }

    /**
     * Выбирает оптимальный режим для запроса.
     */
    private fun selectMode(query: String): SearchMode {
        val textScore = textSearch.isApplicable(query)
        val vectorScore = vectorSearch.isApplicable(query)
        val graphScore = graphSearch.isApplicable(query)

        return when {
            // Если текстовый поиск очень хорош - используем FAST
            textScore > 0.8f && vectorScore < 0.6f -> SearchMode.FAST

            // Если нужен граф - DEEP
            graphScore > 0.7f -> SearchMode.DEEP

            // По умолчанию BALANCED
            else -> SearchMode.BALANCED
        }
    }
}
