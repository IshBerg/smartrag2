/**
 * SmartRAG v2 - Import Job
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Структуры для отслеживания процесса импорта.
 */
package com.yakkismart.smartrag.import

/**
 * Задача импорта.
 */
data class ImportJob(
    val id: String,
    val totalItems: Int,
    val processedItems: Int = 0,
    val status: ImportStatus = ImportStatus.PENDING,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val errors: List<String> = emptyList()
) {
    val progress: Float
        get() = if (totalItems > 0) processedItems.toFloat() / totalItems else 0f

    val isComplete: Boolean
        get() = status == ImportStatus.COMPLETED || status == ImportStatus.FAILED
}

/**
 * Статус импорта.
 */
enum class ImportStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

/**
 * Документ для импорта.
 */
data class ImportDocument(
    val type: String,              // 'sms', 'email', 'note', 'document'
    val source: String?,           // Источник данных
    val text: String,              // Текст документа
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Результат импорта одного документа.
 */
data class ImportResult(
    val contentId: Long,
    val chunksCount: Int,
    val entitiesCount: Int,
    val graphNodesAdded: Int,
    val graphEdgesAdded: Int
)
