/**
 * SmartRAG v2 - Search Strategy Interface
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Интерфейс для различных стратегий поиска.
 */
package com.yakkismart.smartrag.search

import com.yakkismart.smartrag.api.SearchParams
import com.yakkismart.smartrag.api.SearchResult

/**
 * Интерфейс стратегии поиска.
 */
interface SearchStrategy {

    /**
     * Имя стратегии для логирования.
     */
    val name: String

    /**
     * Выполнить поиск по запросу.
     */
    suspend fun search(query: String, params: SearchParams): Result<SearchResult>

    /**
     * Проверить применимость стратегии к запросу.
     * @return 0.0-1.0, где 1.0 = идеально подходит
     */
    fun isApplicable(query: String): Float
}

/**
 * Типы стратегий поиска.
 */
enum class SearchStrategyType {
    TEXT,       // FTS5 полнотекстовый поиск
    VECTOR,     // Векторный семантический поиск
    GRAPH,      // Поиск через граф связей
    HYBRID      // Комбинация всех стратегий
}
