/**
 * SmartRAG v2 - ONNX Embedding Engine
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Генерация embeddings через ONNX Runtime.
 * Модель: all-MiniLM-L6-v2 (384 dimensions).
 */
package com.yakkismart.smartrag.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * ONNX движок для embeddings.
 */
class OnnxEmbeddingEngine(
    context: Context,
    private val modelName: String = "all-MiniLM-L6-v2.onnx",
    private val dimensions: Int = 384,
    cacheSize: Int = 1000
) {

    private val modelManager = ModelManager(context)
    private val tokenizer = Tokenizer()
    private val cache = EmbeddingCache(cacheSize)

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false

    /**
     * Инициализирует ONNX Runtime и загружает модель.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext Result.success(Unit)
            }

            // Получаем путь к модели
            val modelPath = modelManager.getModelPath(modelName).getOrThrow()

            // Создаем ONNX environment
            ortEnv = OrtEnvironment.getEnvironment()

            // Создаем сессию
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(2) // Для CPU

            session = ortEnv?.createSession(modelPath, sessionOptions)

            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Генерирует embedding для текста.
     */
    suspend fun encode(text: String): Result<FloatArray> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                return@withContext Result.failure(Exception("Engine not initialized"))
            }

            // Проверяем кэш
            cache.get(text)?.let {
                return@withContext Result.success(it)
            }

            // Токенизация
            val tokenized = tokenizer.tokenize(text)

            // Создаем ONNX tensors
            val inputIds = OnnxTensor.createTensor(
                ortEnv,
                arrayOf(tokenized.inputIds)
            )

            val attentionMask = OnnxTensor.createTensor(
                ortEnv,
                arrayOf(tokenized.attentionMask)
            )

            // Запускаем инференс
            val inputs = mapOf(
                "input_ids" to inputIds,
                "attention_mask" to attentionMask
            )

            val output = session?.run(inputs)

            // Извлекаем embedding
            // Модель возвращает [batch_size, seq_length, hidden_size]
            // Берем [CLS] токен (первый) или mean pooling
            val outputTensor = output?.get(0)?.value as? Array<*>
            val embedding = extractEmbedding(outputTensor)

            // Нормализуем
            val normalized = normalize(embedding)

            // Сохраняем в кэш
            cache.put(text, normalized)

            // Освобождаем ресурсы
            inputIds.close()
            attentionMask.close()
            output?.close()

            Result.success(normalized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Генерирует embeddings для нескольких текстов.
     */
    suspend fun encodeBatch(texts: List<String>): Result<List<FloatArray>> =
        withContext(Dispatchers.IO) {
            try {
                // Простая реализация - по одному
                // Для production оптимизировать через batch processing
                val embeddings = texts.map { text ->
                    encode(text).getOrThrow()
                }
                Result.success(embeddings)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Извлекает embedding из output tensor.
     */
    private fun extractEmbedding(outputTensor: Any?): FloatArray {
        // Упрощенная версия - берем первый токен [CLS]
        // outputTensor имеет форму [1, seq_length, hidden_size]

        return when (outputTensor) {
            is Array<*> -> {
                val batch = outputTensor[0] as? Array<*>
                val clsToken = batch?.get(0) as? FloatArray
                clsToken ?: FloatArray(dimensions) { 0f }
            }
            else -> FloatArray(dimensions) { 0f }
        }
    }

    /**
     * Нормализует вектор (L2 нормализация).
     */
    private fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) {
            FloatArray(vector.size) { vector[it] / norm }
        } else {
            vector
        }
    }

    /**
     * Получает статистику кэша.
     */
    fun getCacheStats(): CacheStats {
        return cache.getStats()
    }

    /**
     * Очищает кэш.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Закрывает ресурсы.
     */
    fun close() {
        session?.close()
        ortEnv?.close()
        isInitialized = false
    }
}
