/**
 * SmartRAG v2 - Graph Search Strategy
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Поиск через граф связей.
 * Находит связанные сущности и документы.
 */
package com.yakkismart.smartrag.search

import com.yakkismart.smartrag.api.SearchParams
import com.yakkismart.smartrag.api.SearchResult
import com.yakkismart.smartrag.api.SearchResultItem
import com.yakkismart.smartrag.storage.SQLiteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Граф-поиск через связи.
 */
class GraphSearch(
    private val sqliteManager: SQLiteManager
) : SearchStrategy {

    override val name: String = "GraphSearch"

    override suspend fun search(
        query: String,
        params: SearchParams
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val db = sqliteManager.readableDatabase

            // 1. Найти узлы графа по имени
            val nodesCursor = db.rawQuery(
                """
                SELECT id, name, node_type, importance
                FROM graph_nodes
                WHERE name LIKE ? OR canonical_name LIKE ?
                ORDER BY importance DESC, mention_count DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf("%$query%", "%$query%", params.limit.toString())
            )

            val nodeIds = mutableListOf<Long>()
            val nodeScores = mutableMapOf<Long, Float>()

            while (nodesCursor.moveToNext()) {
                val id = nodesCursor.getLong(0)
                val importance = nodesCursor.getFloat(3)
                nodeIds.add(id)
                nodeScores[id] = importance
            }
            nodesCursor.close()

            if (nodeIds.isEmpty()) {
                return@withContext Result.success(
                    SearchResult(
                        items = emptyList(),
                        totalCount = 0,
                        searchTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            }

            // 2. Найти контент связанный с этими узлами
            val placeholders = nodeIds.joinToString(",") { "?" }
            val contentCursor = db.rawQuery(
                """
                SELECT DISTINCT c.id, c.type, c.source, c.original_text,
                       c.created_at, cn.relevance
                FROM content c
                JOIN content_nodes cn ON c.id = cn.content_id
                WHERE cn.node_id IN ($placeholders)
                ORDER BY cn.relevance DESC
                LIMIT ?
                """.trimIndent(),
                (nodeIds.map { it.toString() } + params.limit.toString()).toTypedArray()
            )

            val items = mutableListOf<SearchResultItem>()

            while (contentCursor.moveToNext()) {
                val id = contentCursor.getLong(0)
                val type = contentCursor.getString(1)
                val source = contentCursor.getString(2)
                val text = contentCursor.getString(3)
                val createdAt = contentCursor.getLong(4)
                val relevance = contentCursor.getFloat(5)

                items.add(
                    SearchResultItem(
                        id = id,
                        text = text,
                        score = relevance,
                        metadata = mapOf(
                            "type" to type,
                            "source" to (source ?: "unknown"),
                            "created_at" to createdAt,
                            "graph_relevance" to relevance
                        )
                    )
                )
            }
            contentCursor.close()

            Result.success(
                SearchResult(
                    items = items.filter { it.score >= params.minScore },
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
        // - Имен людей/организаций
        // - Специфических сущностей
        // - Прописных букв (имена собственные)
        val hasProperNouns = query.split(" ").any {
            it.firstOrNull()?.isUpperCase() == true
        }

        return when {
            hasProperNouns -> 0.8f  // Есть имена собственные
            query.length < 20 -> 0.6f  // Короткий запрос (вероятно сущность)
            else -> 0.3f  // Длинный запрос
        }
    }
}
