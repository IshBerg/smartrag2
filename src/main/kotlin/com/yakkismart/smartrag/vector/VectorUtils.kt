/**
 * SmartRAG v2 - Vector Utilities
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag.vector

import kotlin.math.sqrt

/**
 * Утилиты для работы с векторами.
 */
object VectorUtils {

    /**
     * Вычисляет косинусное сходство между двумя векторами.
     * Результат в диапазоне [0..1], где 1 = идентичные векторы.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimensions" }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0.0 || normB == 0.0) return 0f

        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    /**
     * Нормализует вектор (L2 нормализация).
     */
    fun normalize(vector: FloatArray): FloatArray {
        var norm = 0.0
        for (v in vector) {
            norm += v * v
        }
        norm = sqrt(norm)

        if (norm == 0.0) return vector

        return FloatArray(vector.size) { i ->
            (vector[i] / norm).toFloat()
        }
    }

    /**
     * Сериализует вектор в BLOB для SQLite.
     */
    fun serializeVector(vector: FloatArray): ByteArray {
        val buffer = ByteArray(vector.size * 4)
        for (i in vector.indices) {
            val bits = vector[i].toBits()
            buffer[i * 4] = (bits shr 24).toByte()
            buffer[i * 4 + 1] = (bits shr 16).toByte()
            buffer[i * 4 + 2] = (bits shr 8).toByte()
            buffer[i * 4 + 3] = bits.toByte()
        }
        return buffer
    }

    /**
     * Десериализует вектор из BLOB.
     */
    fun deserializeVector(blob: ByteArray): FloatArray {
        val size = blob.size / 4
        val vector = FloatArray(size)
        for (i in 0 until size) {
            val bits = ((blob[i * 4].toInt() and 0xFF) shl 24) or
                      ((blob[i * 4 + 1].toInt() and 0xFF) shl 16) or
                      ((blob[i * 4 + 2].toInt() and 0xFF) shl 8) or
                      (blob[i * 4 + 3].toInt() and 0xFF)
            vector[i] = Float.fromBits(bits)
        }
        return vector
    }
}
