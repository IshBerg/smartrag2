/**
 * SmartRAG v2 - SQLite Database Manager
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Управление SQLite базой данных.
 * Выполняет инициализацию, CRUD операции, транзакции.
 */
package com.yakkismart.smartrag.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Менеджер SQLite базы данных.
 */
class SQLiteManager(
    private val context: Context,
    dbName: String
) : SQLiteOpenHelper(context, dbName, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Читаем и выполняем schema.sql
        val schema = readSchemaFromAssets()
        schema.split(";").forEach { statement ->
            val trimmed = statement.trim()
            if (trimmed.isNotEmpty()) {
                db.execSQL(trimmed)
            }
        }

        // Включаем WAL mode для лучшей производительности
        db.execSQL("PRAGMA journal_mode = WAL")
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA cache_size = -64000") // 64MB
        db.execSQL("PRAGMA temp_store = MEMORY")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // TODO: Implement migrations
    }

    /**
     * Читает schema.sql из resources.
     */
    private fun readSchemaFromAssets(): String {
        return try {
            val inputStream = context.assets.open("schema.sql")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use { it.readText() }
        } catch (e: Exception) {
            throw RuntimeException("Failed to read schema.sql", e)
        }
    }

    /**
     * Выполняет операцию в транзакции.
     */
    suspend fun <T> transaction(block: suspend (SQLiteDatabase) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val result = block(db)
                db.setTransactionSuccessful()
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                db.endTransaction()
            }
        }

    /**
     * Вставка контента.
     */
    suspend fun insertContent(
        type: String,
        source: String?,
        originalText: String,
        metadata: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            val now = System.currentTimeMillis() / 1000

            val sql = """
                INSERT INTO content (type, source, original_text, created_at, imported_at, metadata)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            db.execSQL(sql, arrayOf<Any?>(type, source, originalText, now, now, metadata))

            // Получаем ID последней вставки
            val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
            val id = if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            cursor.close()

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Полнотекстовый поиск через FTS5.
     */
    suspend fun searchFTS(query: String, limit: Int = 10): Result<List<Long>> =
        withContext(Dispatchers.IO) {
            try {
                val db = readableDatabase
                val cursor = db.rawQuery(
                    "SELECT content_id FROM fts_content WHERE fts_content MATCH ? LIMIT ?",
                    arrayOf(query, limit.toString())
                )

                val ids = mutableListOf<Long>()
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0))
                }
                cursor.close()

                Result.success(ids)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Получение контента по ID.
     */
    suspend fun getContentById(id: Long): Result<ContentRow?> =
        withContext(Dispatchers.IO) {
            try {
                val db = readableDatabase
                val cursor = db.rawQuery(
                    "SELECT * FROM content WHERE id = ?",
                    arrayOf(id.toString())
                )

                val content = if (cursor.moveToFirst()) {
                    ContentRow(
                        id = cursor.getLong(0),
                        type = cursor.getString(1),
                        source = cursor.getString(2),
                        originalText = cursor.getString(3),
                        createdAt = cursor.getLong(7),
                        importedAt = cursor.getLong(8)
                    )
                } else null

                cursor.close()
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Получение списка контента по ID.
     */
    suspend fun getContentByIds(ids: List<Long>): Result<List<ContentRow>> =
        withContext(Dispatchers.IO) {
            try {
                if (ids.isEmpty()) return@withContext Result.success(emptyList())

                val db = readableDatabase
                val placeholders = ids.joinToString(",") { "?" }
                val cursor = db.rawQuery(
                    "SELECT * FROM content WHERE id IN ($placeholders)",
                    ids.map { it.toString() }.toTypedArray()
                )

                val contents = mutableListOf<ContentRow>()
                while (cursor.moveToNext()) {
                    contents.add(
                        ContentRow(
                            id = cursor.getLong(0),
                            type = cursor.getString(1),
                            source = cursor.getString(2),
                            originalText = cursor.getString(3),
                            createdAt = cursor.getLong(7),
                            importedAt = cursor.getLong(8)
                        )
                    )
                }
                cursor.close()

                Result.success(contents)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Очистка всех данных.
     */
    suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            db.execSQL("DELETE FROM content")
            db.execSQL("DELETE FROM fts_content")
            db.execSQL("DELETE FROM graph_nodes")
            db.execSQL("DELETE FROM graph_edges")
            db.execSQL("DELETE FROM content_nodes")
            db.execSQL("DELETE FROM search_cache")
            db.execSQL("DELETE FROM vectors")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получение статистики БД.
     */
    suspend fun getStats(): Result<DatabaseStats> = withContext(Dispatchers.IO) {
        try {
            val db = readableDatabase

            fun count(table: String): Long {
                val cursor = db.rawQuery("SELECT COUNT(*) FROM $table", null)
                val count = if (cursor.moveToFirst()) cursor.getLong(0) else 0
                cursor.close()
                return count
            }

            Result.success(
                DatabaseStats(
                    contentCount = count("content"),
                    nodesCount = count("graph_nodes"),
                    edgesCount = count("graph_edges"),
                    vectorsCount = count("vectors")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Простая модель строки контента.
 */
data class ContentRow(
    val id: Long,
    val type: String,
    val source: String?,
    val originalText: String,
    val createdAt: Long,
    val importedAt: Long
)

/**
 * Статистика базы данных.
 */
data class DatabaseStats(
    val contentCount: Long,
    val nodesCount: Long,
    val edgesCount: Long,
    val vectorsCount: Long
)
