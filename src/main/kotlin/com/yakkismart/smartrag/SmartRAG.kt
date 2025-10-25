/**
 * SmartRAG v2 - Main API Class
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag

import android.content.Context
import com.yakkismart.smartrag.storage.SQLiteManager

class SmartRAG private constructor(
    private val context: Context,
    private val config: SmartRAGConfig
) {

    private val sqliteManager: SQLiteManager = SQLiteManager(context, config.dbName)

    init {
        // Инициализация базы данных
        sqliteManager.writableDatabase
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

    /**
     * Закрытие ресурсов.
     */
    fun close() {
        sqliteManager.close()
    }

    companion object {
        const val VERSION = "2.0.0"
    }
}
