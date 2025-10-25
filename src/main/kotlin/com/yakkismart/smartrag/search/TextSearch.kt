/**
 * SmartRAG v2 - Text Search Strategy
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Быстрый полнотекстовый поиск через FTS5.
 * Идеален для точных совпадений по ключевым словам.
 */
package com.yakkismart.smartrag.search

import com.yakkismart.smartrag.api.SearchParams
import com.yakkismart.smartrag.api.SearchResult
import com.yakkismart.smartrag.api.SearchResultItem
import com.yakkismart.smartrag.storage.SQLiteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FTS5 полнотекстовый поиск.
 */
class TextSearch(
    private val sqliteManager: SQLiteManager
) : SearchStrategy {

    override val name: String = "TextSearch"

    override suspend fun search(
        query: String,
        params: SearchParams
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // FTS5 поиск
            val idsResult = sqliteManager.searchFTS(query, params.limit)
            val ids = idsResult.getOrThrow()

            if (ids.isEmpty()) {
                return@withContext Result.success(
                    SearchResult(
                        items = emptyList(),
                        totalCount = 0,
                        searchTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            }

            // Получаем контент по ID
            val contentsResult = sqliteManager.getContentByIds(ids)
            val contents = contentsResult.getOrThrow()

            // Формируем результаты
            val items = contents.mapIndexed { index, content ->
                // FTS5 score - нормализуем в диапазон 0-1
                val score = 1.0f - (index.toFloat() / ids.size.toFloat())

                SearchResultItem(
                    id = content.id,
                    text = content.originalText,
                    score = score,
                    metadata = mapOf(
                        "type" to content.type,
                        "source" to (content.source ?: "unknown"),
                        "created_at" to content.createdAt
                    )
                )
            }

            Result.success(
                SearchResult(
                    items = items,
                    totalCount = items.size,
                    searchTimeMs = System.currentTimeMillis() - startTime
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isApplicable(query: String): Float {
        // Хорошо работает для:
        // - Точных ключевых слов
        // - Коротких запросов
        // - Запросов с кавычками
        return when {
            query.contains("\"") -> 0.9f  // Запрос в кавычках
            query.split(" ").size <= 3 -> 0.8f  // Короткий запрос
            else -> 0.6f  // Обычный запрос
        }
    }
}
