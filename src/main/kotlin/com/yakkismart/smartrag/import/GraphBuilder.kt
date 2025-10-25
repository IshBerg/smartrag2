/**
 * SmartRAG v2 - Graph Builder
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Строит граф знаний из извлеченных сущностей.
 */
package com.yakkismart.smartrag.import

import com.yakkismart.smartrag.storage.SQLiteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builder для графа знаний.
 */
class GraphBuilder(
    private val sqliteManager: SQLiteManager
) {

    /**
     * Добавляет сущности в граф и связывает с контентом.
     */
    suspend fun addEntities(
        contentId: Long,
        entities: List<Entity>
    ): Result<GraphBuildResult> = withContext(Dispatchers.IO) {
        try {
            val db = sqliteManager.writableDatabase
            val now = System.currentTimeMillis() / 1000

            var nodesAdded = 0
            var edgesAdded = 0

            db.beginTransaction()
            try {
                entities.forEach { entity ->
                    val canonicalName = normalizeEntityName(entity.value)
                    val nodeType = entity.type.name.lowercase()

                    // Вставка или обновление узла
                    val nodeId = insertOrUpdateNode(
                        db = db,
                        nodeType = nodeType,
                        name = entity.value,
                        canonicalName = canonicalName,
                        now = now
                    )

                    if (nodeId > 0) {
                        nodesAdded++

                        // Связываем контент с узлом
                        val sql = """
                            INSERT OR REPLACE INTO content_nodes
                            (content_id, node_id, relevance, position, context)
                            VALUES (?, ?, ?, ?, ?)
                        """.trimIndent()

                        db.execSQL(
                            sql,
                            arrayOf(
                                contentId,
                                nodeId,
                                entity.confidence,
                                entity.position,
                                entity.context
                            )
                        )
                    }
                }

                // Создаем связи между сущностями в одном документе
                if (entities.size > 1) {
                    edgesAdded = createCooccurrenceEdges(db, contentId, entities, now)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            Result.success(
                GraphBuildResult(
                    nodesAdded = nodesAdded,
                    edgesAdded = edgesAdded
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Вставляет или обновляет узел графа.
     */
    private fun insertOrUpdateNode(
        db: android.database.sqlite.SQLiteDatabase,
        nodeType: String,
        name: String,
        canonicalName: String,
        now: Long
    ): Long {
        // Проверяем существование узла
        val cursor = db.rawQuery(
            "SELECT id FROM graph_nodes WHERE node_type = ? AND canonical_name = ?",
            arrayOf(nodeType, canonicalName)
        )

        val existingId = if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        cursor.close()

        return if (existingId > 0) {
            // Обновляем существующий узел
            val sql = """
                UPDATE graph_nodes
                SET last_seen = ?, mention_count = mention_count + 1
                WHERE id = ?
            """.trimIndent()
            db.execSQL(sql, arrayOf(now, existingId))
            existingId
        } else {
            // Вставляем новый узел
            val sql = """
                INSERT INTO graph_nodes
                (node_type, name, canonical_name, first_seen, last_seen, mention_count, importance)
                VALUES (?, ?, ?, ?, ?, 1, 1.0)
            """.trimIndent()
            db.execSQL(sql, arrayOf(nodeType, name, canonicalName, now, now))

            // Получаем ID
            val idCursor = db.rawQuery("SELECT last_insert_rowid()", null)
            val id = if (idCursor.moveToFirst()) idCursor.getLong(0) else -1L
            idCursor.close()
            id
        }
    }

    /**
     * Создает связи совместной встречаемости.
     */
    private fun createCooccurrenceEdges(
        db: android.database.sqlite.SQLiteDatabase,
        contentId: Long,
        entities: List<Entity>,
        now: Long
    ): Int {
        var edgesCount = 0

        // Получаем ID узлов для всех сущностей
        val nodeIds = entities.mapNotNull { entity ->
            val canonicalName = normalizeEntityName(entity.value)
            val cursor = db.rawQuery(
                "SELECT id FROM graph_nodes WHERE canonical_name = ?",
                arrayOf(canonicalName)
            )
            val id = if (cursor.moveToFirst()) cursor.getLong(0) else null
            cursor.close()
            id
        }

        // Создаем связи между всеми парами
        for (i in nodeIds.indices) {
            for (j in (i + 1) until nodeIds.size) {
                val fromId = nodeIds[i]
                val toId = nodeIds[j]

                // Проверяем существование связи
                val cursor = db.rawQuery(
                    """
                    SELECT id, weight FROM graph_edges
                    WHERE from_node_id = ? AND to_node_id = ?
                    """.trimIndent(),
                    arrayOf(fromId.toString(), toId.toString())
                )

                if (cursor.moveToFirst()) {
                    // Обновляем вес
                    val edgeId = cursor.getLong(0)
                    val currentWeight = cursor.getFloat(1)
                    cursor.close()

                    db.execSQL(
                        "UPDATE graph_edges SET weight = ? WHERE id = ?",
                        arrayOf(currentWeight + 0.1f, edgeId)
                    )
                } else {
                    cursor.close()

                    // Создаем новую связь
                    val sql = """
                        INSERT INTO graph_edges
                        (from_node_id, to_node_id, edge_type, weight, content_id, created_at)
                        VALUES (?, ?, 'co_occurs', 1.0, ?, ?)
                    """.trimIndent()
                    db.execSQL(sql, arrayOf(fromId, toId, contentId, now))
                    edgesCount++
                }
            }
        }

        return edgesCount
    }

    /**
     * Нормализует имя сущности.
     */
    private fun normalizeEntityName(name: String): String {
        return name.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }
}

/**
 * Результат построения графа.
 */
data class GraphBuildResult(
    val nodesAdded: Int,
    val edgesAdded: Int
)
