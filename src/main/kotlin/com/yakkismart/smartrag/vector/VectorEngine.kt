/**
 * SmartRAG v2 - Vector Engine Interface
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Интерфейс для векторного поиска.
 * Реализации: SQLiteVectorEngine (fallback), RustVectorEngine (production).
 */
package com.yakkismart.smartrag.vector

/**
 * Интерфейс векторного движка.
 */
interface VectorEngine {

    /**
     * Добавить вектор с привязкой к content ID.
     */
    suspend fun addVector(contentId: Long, vector: FloatArray): Result<Unit>

    /**
     * Добавить несколько векторов batch операцией.
     */
    suspend fun addVectorsBatch(vectors: List<Pair<Long, FloatArray>>): Result<Unit>

    /**
     * Поиск K ближайших соседей.
     * @return список пар (contentId, score) отсортированных по убыванию score
     */
    suspend fun search(queryVector: FloatArray, k: Int): Result<List<VectorSearchResult>>

    /**
     * Получить размер индекса (количество векторов).
     */
    suspend fun getIndexSize(): Result<Int>

    /**
     * Очистить все векторы.
     */
    suspend fun clear(): Result<Unit>

    /**
     * Закрыть ресурсы.
     */
    fun close()
}

/**
 * Результат векторного поиска.
 */
data class VectorSearchResult(
    val contentId: Long,
    val score: Float  // Косинусное сходство [0..1], чем больше - тем лучше
)
