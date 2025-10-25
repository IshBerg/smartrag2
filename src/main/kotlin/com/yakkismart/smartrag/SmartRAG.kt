/**
 * SmartRAG v2 - Main API Class
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag

import android.content.Context
import com.yakkismart.smartrag.embedding.OnnxEmbeddingEngine
import com.yakkismart.smartrag.embedding.CacheStats

class SmartRAG private constructor(
    private val context: Context,
    private val config: SmartRAGConfig
) {

    // ONNX Embedding Engine
    private val onnxEngine: OnnxEmbeddingEngine = OnnxEmbeddingEngine(
        context = context,
        dimensions = config.embeddingDimensions
    )

    /**
     * Инициализация ONNX движка (вызвать перед использованием).
     */
    suspend fun initializeEmbeddings(): Result<Unit> {
        return onnxEngine.initialize()
    }

    /**
     * Генерирует embedding для текста.
     */
    suspend fun generateEmbedding(text: String): Result<FloatArray> {
        return onnxEngine.encode(text)
    }

    /**
     * Генерирует embeddings для нескольких текстов.
     */
    suspend fun generateEmbeddings(texts: List<String>): Result<List<FloatArray>> {
        return onnxEngine.encodeBatch(texts)
    }

    /**
     * Получает статистику кэша embeddings.
     */
    fun getEmbeddingCacheStats(): CacheStats {
        return onnxEngine.getCacheStats()
    }

    /**
     * Очищает кэш embeddings.
     */
    fun clearEmbeddingCache() {
        onnxEngine.clearCache()
    }

    /**
     * Закрывает ресурсы.
     */
    fun close() {
        onnxEngine.close()
    }

    class Builder(private val context: Context) {
        private var config = SmartRAGConfig.default()

        fun setDatabaseName(name: String) = apply {
            config = config.copy(dbName = name)
        }

        fun setVectorBackend(backend: VectorBackend) = apply {
            config = config.copy(vectorBackend = backend)
        }

        fun setEmbeddingDimensions(dimensions: Int) = apply {
            config = config.copy(embeddingDimensions = dimensions)
        }

        fun build(): SmartRAG = SmartRAG(context, config)
    }

    companion object {
        const val VERSION = "2.0.0"
    }
}
