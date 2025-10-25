/**
 * SmartRAG v2 - Vector Search Strategy
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Семантический поиск через векторные embeddings.
 * Находит концептуально похожие документы.
 */
package com.yakkismart.smartrag.search

import com.yakkismart.smartrag.api.SearchParams
import com.yakkismart.smartrag.api.SearchResult
import com.yakkismart.smartrag.api.SearchResultItem
import com.yakkismart.smartrag.storage.SQLiteManager
import com.yakkismart.smartrag.vector.VectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Векторный семантический поиск.
 */
class VectorSearch(
    private val vectorEngine: VectorEngine,
    private val sqliteManager: SQLiteManager,
    private val embeddingFunction: suspend (String) -> FloatArray
) : SearchStrategy {

    override val name: String = "VectorSearch"

    override suspend fun search(
        query: String,
        params: SearchParams
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Генерируем embedding для запроса
            val queryVector = embeddingFunction(query)

            // Векторный поиск
            val vectorResults = vectorEngine.search(queryVector, params.limit).getOrThrow()

            if (vectorResults.isEmpty()) {
                return@withContext Result.success(
                    SearchResult(
                        items = emptyList(),
                        totalCount = 0,
                        searchTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            }

            // Получаем контент по ID
            val ids = vectorResults.map { it.contentId }
            val contentsResult = sqliteManager.getContentByIds(ids)
            val contents = contentsResult.getOrThrow()

            // Создаем map для быстрого доступа
            val contentMap = contents.associateBy { it.id }

            // Формируем результаты с оригинальными scores
            val items = vectorResults.mapNotNull { result ->
                contentMap[result.contentId]?.let { content ->
                    SearchResultItem(
                        id = content.id,
                        text = content.originalText,
                        score = result.score,
                        metadata = mapOf(
                            "type" to content.type,
                            "source" to (content.source ?: "unknown"),
                            "created_at" to content.createdAt,
                            "similarity" to result.score
                        )
                    )
                }
            }.filter { it.score >= params.minScore }

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
        // - Длинных запросов (концептуальный поиск)
        // - Вопросов
        // - Семантических запросов
        return when {
            query.contains("?") -> 0.9f  // Вопрос
            query.split(" ").size > 5 -> 0.85f  // Длинный запрос
            query.split(" ").size > 3 -> 0.75f  // Средний запрос
            else -> 0.5f  // Короткий запрос
        }
    }
}
