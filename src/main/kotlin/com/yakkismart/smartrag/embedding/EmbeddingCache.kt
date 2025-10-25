/**
 * SmartRAG v2 - Embedding Cache
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * LRU кэш для embeddings.
 * Экономит вычисления для частых запросов.
 */
package com.yakkismart.smartrag.embedding

import java.security.MessageDigest

/**
 * LRU кэш для embeddings.
 */
class EmbeddingCache(
    private val maxSize: Int = 1000
) {

    private val cache = object : LinkedHashMap<String, FloatArray>(
        maxSize,
        0.75f,
        true // access-order (LRU)
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean {
            return size > maxSize
        }
    }

    private var hits = 0L
    private var misses = 0L

    /**
     * Получает embedding из кэша.
     */
    @Synchronized
    fun get(text: String): FloatArray? {
        val key = hashText(text)
        val value = cache[key]

        if (value != null) {
            hits++
        } else {
            misses++
        }

        return value
    }

    /**
     * Сохраняет embedding в кэш.
     */
    @Synchronized
    fun put(text: String, embedding: FloatArray) {
        val key = hashText(text)
        cache[key] = embedding
    }

    /**
     * Очищает кэш.
     */
    @Synchronized
    fun clear() {
        cache.clear()
        hits = 0L
        misses = 0L
    }

    /**
     * Получает статистику кэша.
     */
    @Synchronized
    fun getStats(): CacheStats {
        val total = hits + misses
        val hitRate = if (total > 0) hits.toFloat() / total else 0f

        return CacheStats(
            size = cache.size,
            maxSize = maxSize,
            hits = hits,
            misses = misses,
            hitRate = hitRate
        )
    }

    /**
     * Хеширует текст для ключа.
     */
    private fun hashText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Статистика кэша.
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val hitRate: Float
)
