/**
 * SmartRAG v2 - Main API Class
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag

import android.content.Context
import com.yakkismart.smartrag.storage.SQLiteManager
import com.yakkismart.smartrag.vector.VectorEngine
import com.yakkismart.smartrag.vector.SQLiteVectorEngine
import com.yakkismart.smartrag.vector.RustVectorEngine

class SmartRAG private constructor(
    private val context: Context,
    private val config: SmartRAGConfig
) {

    private val sqliteManager: SQLiteManager = SQLiteManager(context, config.dbName)
    private val vectorEngine: VectorEngine

    init {
        // Инициализация базы данных
        sqliteManager.writableDatabase

        // Выбор векторного движка
        vectorEngine = when (config.vectorBackend) {
            VectorBackend.RUST -> {
                try {
                    RustVectorEngine(context, config.embeddingDimensions, config.maxVectorElements)
                } catch (e: Exception) {
                    // Fallback если Rust не доступен
                    SQLiteVectorEngine(sqliteManager, config.embeddingDimensions)
                }
            }
            VectorBackend.SQLITE_FALLBACK -> {
                SQLiteVectorEngine(sqliteManager, config.embeddingDimensions)
            }
        }
    }

    /**
     * Закрыть все ресурсы SmartRAG.
     */
    fun close() {
        vectorEngine.close()
        sqliteManager.close()
    }

    class Builder(private val context: Context) {
        private var config = SmartRAGConfig.default()

        fun setDatabaseName(name: String) = apply {
            config = config.copy(dbName = name)
        }

        fun setVectorBackend(backend: VectorBackend) = apply {
            config = config.copy(vectorBackend = backend)
        }

        fun build(): SmartRAG = SmartRAG(context, config)
    }

    companion object {
        const val VERSION = "2.0.0"
    }
}
