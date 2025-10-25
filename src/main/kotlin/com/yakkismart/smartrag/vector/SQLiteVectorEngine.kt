/**
 * SmartRAG v2 - SQLite Vector Engine (Fallback)
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Fallback векторный движок на SQLite.
 * Использует brute-force поиск с косинусным сходством.
 * Медленный, но надежный и работает везде.
 */
package com.yakkismart.smartrag.vector

import com.yakkismart.smartrag.storage.SQLiteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite векторный движок (fallback).
 */
class SQLiteVectorEngine(
    private val sqliteManager: SQLiteManager,
    private val dimensions: Int
) : VectorEngine {

    override suspend fun addVector(contentId: Long, vector: FloatArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(vector.size == dimensions) {
                    "Vector dimension mismatch: expected $dimensions, got ${vector.size}"
                }

                val db = sqliteManager.writableDatabase
                val blob = VectorUtils.serializeVector(vector)
                val now = System.currentTimeMillis() / 1000

                val sql = """
                    INSERT OR REPLACE INTO vectors (content_id, vector, dimensions, created_at)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()

                db.execSQL(sql, arrayOf(contentId, blob, dimensions, now))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun addVectorsBatch(
        vectors: List<Pair<Long, FloatArray>>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = sqliteManager.writableDatabase
            db.beginTransaction()
            try {
                vectors.forEach { (contentId, vector) ->
                    require(vector.size == dimensions) {
                        "Vector dimension mismatch"
                    }

                    val blob = VectorUtils.serializeVector(vector)
                    val now = System.currentTimeMillis() / 1000

                    val sql = """
                        INSERT OR REPLACE INTO vectors (content_id, vector, dimensions, created_at)
                        VALUES (?, ?, ?, ?)
                    """.trimIndent()

                    db.execSQL(sql, arrayOf(contentId, blob, dimensions, now))
                }
                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun search(
        queryVector: FloatArray,
        k: Int
    ): Result<List<VectorSearchResult>> = withContext(Dispatchers.IO) {
        try {
            require(queryVector.size == dimensions) {
                "Query vector dimension mismatch"
            }

            val db = sqliteManager.readableDatabase
            val cursor = db.rawQuery(
                "SELECT content_id, vector FROM vectors WHERE dimensions = ?",
                arrayOf(dimensions.toString())
            )

            val results = mutableListOf<VectorSearchResult>()

            while (cursor.moveToNext()) {
                val contentId = cursor.getLong(0)
                val blob = cursor.getBlob(1)
                val vector = VectorUtils.deserializeVector(blob)

                val similarity = VectorUtils.cosineSimilarity(queryVector, vector)
                results.add(VectorSearchResult(contentId, similarity))
            }
            cursor.close()

            // Сортируем по score (убывание) и берем топ-K
            val topK = results
                .sortedByDescending { it.score }
                .take(k)

            Result.success(topK)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getIndexSize(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val db = sqliteManager.readableDatabase
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM vectors WHERE dimensions = ?",
                arrayOf(dimensions.toString())
            )

            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            cursor.close()

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = sqliteManager.writableDatabase
            db.execSQL("DELETE FROM vectors")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun close() {
        // SQLiteManager закроется через SmartRAG
    }
}
