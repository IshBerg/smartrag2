/**
 * SmartRAG v2 - Model Manager
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Управление ONNX моделями.
 * Загружает модель из assets или внешнего хранилища.
 */
package com.yakkismart.smartrag.embedding

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Менеджер ONNX моделей.
 */
class ModelManager(
    private val context: Context
) {

    companion object {
        private const val MODELS_DIR = "models"
        private const val DEFAULT_MODEL = "all-MiniLM-L6-v2.onnx"
    }

    /**
     * Получает путь к модели, копируя из assets если нужно.
     */
    fun getModelPath(modelName: String = DEFAULT_MODEL): Result<String> {
        return try {
            val modelFile = File(context.filesDir, "$MODELS_DIR/$modelName")

            if (!modelFile.exists()) {
                // Копируем из assets
                val result = copyModelFromAssets(modelName, modelFile)
                if (result.isFailure) {
                    return Result.failure(
                        Exception("Failed to copy model from assets: ${result.exceptionOrNull()?.message}")
                    )
                }
            }

            Result.success(modelFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Копирует модель из assets в internal storage.
     */
    private fun copyModelFromAssets(modelName: String, destination: File): Result<Unit> {
        return try {
            // Создаем директорию если не существует
            destination.parentFile?.mkdirs()

            // Копируем файл
            context.assets.open("$MODELS_DIR/$modelName").use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Проверяет наличие модели.
     */
    fun hasModel(modelName: String = DEFAULT_MODEL): Boolean {
        val modelFile = File(context.filesDir, "$MODELS_DIR/$modelName")
        if (modelFile.exists()) return true

        // Проверяем в assets
        return try {
            context.assets.open("$MODELS_DIR/$modelName").close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Получает размер модели в байтах.
     */
    fun getModelSize(modelName: String = DEFAULT_MODEL): Long {
        val modelFile = File(context.filesDir, "$MODELS_DIR/$modelName")
        return if (modelFile.exists()) {
            modelFile.length()
        } else {
            0L
        }
    }
}
