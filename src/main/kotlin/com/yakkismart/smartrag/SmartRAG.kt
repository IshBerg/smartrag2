/**
 * SmartRAG v2 - Main API Class
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag

import android.content.Context
import com.yakkismart.smartrag.api.SearchParams
import com.yakkismart.smartrag.api.SearchResult
import com.yakkismart.smartrag.import.DocumentImporter
import com.yakkismart.smartrag.import.ImportDocument
import com.yakkismart.smartrag.import.ImportJob
import com.yakkismart.smartrag.import.ImportResult
import com.yakkismart.smartrag.search.*
import com.yakkismart.smartrag.storage.SQLiteManager
import com.yakkismart.smartrag.storage.DatabaseStats
import com.yakkismart.smartrag.vector.VectorEngine
import com.yakkismart.smartrag.vector.SQLiteVectorEngine
import com.yakkismart.smartrag.vector.RustVectorEngine

class SmartRAG private constructor(
    private val context: Context,
    private val config: SmartRAGConfig
) {

    private val sqliteManager: SQLiteManager = SQLiteManager(context, config.dbName)
    private val vectorEngine: VectorEngine

    // Поисковые стратегии
    private val textSearch: TextSearch
    private val vectorSearch: VectorSearch
    private val graphSearch: GraphSearch
    private val hybridSearch: HybridSearchEngine
    private val adaptiveRouter: AdaptiveRouter

    // Import pipeline
    private val documentImporter: DocumentImporter

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

        // Инициализация поисковых стратегий
        textSearch = TextSearch(sqliteManager)
        vectorSearch = VectorSearch(
            vectorEngine,
            sqliteManager,
            embeddingFunction = { text ->
                // TODO: Реализуем в Фазе 6 (ONNX Embeddings)
                FloatArray(config.embeddingDimensions) { 0f }
            }
        )
        graphSearch = GraphSearch(sqliteManager)

        hybridSearch = HybridSearchEngine(
            listOf(
                textSearch to 1.0f,
                vectorSearch to 1.0f,
                graphSearch to 0.5f
            )
        )

        adaptiveRouter = AdaptiveRouter(
            textSearch,
            vectorSearch,
            graphSearch,
            hybridSearch
        )

        // Инициализация документного импортера
        documentImporter = DocumentImporter(
            sqliteManager,
            vectorEngine,
            embeddingFunction = { text ->
                // TODO: Implement ONNX embeddings in Phase 6
                FloatArray(config.embeddingDimensions) { 0f }
            }
        )
    }

    /**
     * Быстрый поиск (только FTS5).
     */
    suspend fun searchFast(query: String, params: SearchParams = SearchParams.default()): Result<SearchResult> {
        return adaptiveRouter.search(query, params, AdaptiveRouter.SearchMode.FAST)
    }

    /**
     * Сбалансированный поиск (FTS5 + векторы).
     */
    suspend fun searchBalanced(query: String, params: SearchParams = SearchParams.default()): Result<SearchResult> {
        return adaptiveRouter.search(query, params, AdaptiveRouter.SearchMode.BALANCED)
    }

    /**
     * Глубокий поиск (все стратегии).
     */
    suspend fun searchDeep(query: String, params: SearchParams = SearchParams.default()): Result<SearchResult> {
        return adaptiveRouter.search(query, params, AdaptiveRouter.SearchMode.DEEP)
    }

    /**
     * Автоматический выбор режима поиска.
     */
    suspend fun search(query: String, params: SearchParams = SearchParams.default()): Result<SearchResult> {
        return adaptiveRouter.searchAuto(query, params)
    }

    /**
     * Импорт одного документа.
     */
    suspend fun importDocument(document: ImportDocument): Result<ImportResult> {
        return documentImporter.importDocument(document)
    }

    /**
     * Импорт нескольких документов с progress tracking.
     */
    suspend fun importDocuments(
        documents: List<ImportDocument>,
        onProgress: ((ImportJob) -> Unit)? = null
    ): Result<ImportJob> {
        return documentImporter.importDocuments(documents, onProgress)
    }

    /**
     * Получение статистики базы данных.
     */
    suspend fun getStats(): Result<DatabaseStats> {
        return sqliteManager.getStats()
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
