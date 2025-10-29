/**
 * SmartRAG v2 - Document Importer
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Координирует весь процесс импорта документов.
 */
package com.yakkismart.smartrag.import

import com.yakkismart.smartrag.storage.SQLiteManager
import com.yakkismart.smartrag.vector.VectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Импортер документов.
 */
class DocumentImporter(
    private val sqliteManager: SQLiteManager,
    private val vectorEngine: VectorEngine,
    private val embeddingFunction: suspend (String) -> FloatArray
) {

    private val textChunker = TextChunker()
    private val entityExtractor = EntityExtractor()
    private val graphBuilder = GraphBuilder(sqliteManager)

    companion object {
        private val json = Json { encodeDefaults = true }
    }

    /**
     * Импортирует один документ.
     */
    suspend fun importDocument(document: ImportDocument): Result<ImportResult> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Вставляем контент в базу
                val metadataJson = json.encodeToString(document.metadata)
                val contentResult = sqliteManager.insertContent(
                    type = document.type,
                    source = document.source,
                    originalText = document.text,
                    metadata = metadataJson
                )
                val contentId = contentResult.getOrThrow()

                // 2. Разбиваем на chunks
                val chunks = textChunker.chunk(document.text)

                // 3. Извлекаем сущности
                val entities = entityExtractor.extract(document.text)

                // 4. Строим граф
                val graphResult = graphBuilder.addEntities(contentId, entities).getOrThrow()

                // 5. Генерируем embeddings и добавляем векторы
                chunks.forEach { chunk ->
                    val embedding = embeddingFunction(chunk.text)
                    vectorEngine.addVector(contentId, embedding)
                }

                // 6. Добавляем в FTS5 для полнотекстового поиска
                addToFTS(contentId, document)

                Result.success(
                    ImportResult(
                        contentId = contentId,
                        chunksCount = chunks.size,
                        entitiesCount = entities.size,
                        graphNodesAdded = graphResult.nodesAdded,
                        graphEdgesAdded = graphResult.edgesAdded
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Импортирует несколько документов с progress tracking.
     */
    suspend fun importDocuments(
        documents: List<ImportDocument>,
        onProgress: ((ImportJob) -> Unit)? = null
    ): Result<ImportJob> = withContext(Dispatchers.IO) {
        val jobId = java.util.UUID.randomUUID().toString()
        var job = ImportJob(
            id = jobId,
            totalItems = documents.size,
            status = ImportStatus.PROCESSING
        )

        onProgress?.invoke(job)

        val errors = mutableListOf<String>()

        documents.forEachIndexed { index, document ->
            val result = importDocument(document)

            if (result.isFailure) {
                errors.add("Document $index: ${result.exceptionOrNull()?.message}")
            }

            job = job.copy(
                processedItems = index + 1
            )
            onProgress?.invoke(job)
        }

        job = job.copy(
            status = if (errors.isEmpty()) ImportStatus.COMPLETED else ImportStatus.FAILED,
            endTime = System.currentTimeMillis(),
            errors = errors
        )

        onProgress?.invoke(job)
        Result.success(job)
    }

    /**
     * Добавляет документ в FTS5 индекс.
     */
    private fun addToFTS(contentId: Long, document: ImportDocument) {
        val db = sqliteManager.writableDatabase

        // Извлекаем title из metadata или используем первые слова
        val title = (document.metadata["title"] as? String)
            ?: document.text.take(100)

        // Извлекаем tags
        val tags = (document.metadata["tags"] as? List<*>)
            ?.joinToString(" ") { it.toString() }
            ?: ""

        val sql = """
            INSERT INTO fts_content (content_id, title, content, tags)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        db.execSQL(
            sql,
            arrayOf<Any?>(contentId, title, document.text, tags)
        )
    }
}
