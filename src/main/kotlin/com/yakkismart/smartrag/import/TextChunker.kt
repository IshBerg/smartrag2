/**
 * SmartRAG v2 - Text Chunker
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Разбивает длинные тексты на перекрывающиеся фрагменты (chunks).
 * Использует sentence boundaries для естественных разрывов.
 */
package com.yakkismart.smartrag.import

/**
 * Chunker для текстов.
 */
class TextChunker(
    private val chunkSize: Int = 512,      // Размер чанка в словах
    private val overlapSize: Int = 50       // Overlap между чанками
) {

    companion object {
        private val SENTENCE_DELIMITERS = Regex("[.!?]+\\s+")
        private val WORD_PATTERN = Regex("\\s+")
    }

    /**
     * Разбивает текст на chunks.
     */
    fun chunk(text: String): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        // Разбиваем на предложения
        val sentences = text.split(SENTENCE_DELIMITERS).filter { it.isNotBlank() }

        if (sentences.isEmpty()) return listOf(
            TextChunk(text = text, position = 0, wordCount = countWords(text))
        )

        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var currentWordCount = 0
        var chunkPosition = 0
        var overlapBuffer = mutableListOf<String>()

        sentences.forEach { sentence ->
            val sentenceWords = countWords(sentence)

            // Если добавление предложения превысит размер чанка
            if (currentWordCount + sentenceWords > chunkSize && currentChunk.isNotEmpty()) {
                // Сохраняем текущий чанк
                chunks.add(
                    TextChunk(
                        text = currentChunk.toString().trim(),
                        position = chunkPosition,
                        wordCount = currentWordCount
                    )
                )

                // Начинаем новый чанк с overlap
                currentChunk = StringBuilder()
                overlapBuffer.takeLast(3).forEach { overlapSentence ->
                    currentChunk.append(overlapSentence).append(" ")
                }
                currentWordCount = countWords(currentChunk.toString())
                chunkPosition = chunks.size
            }

            currentChunk.append(sentence).append(" ")
            currentWordCount += sentenceWords

            // Обновляем overlap buffer
            overlapBuffer.add(sentence)
            if (overlapBuffer.size > 5) {
                overlapBuffer.removeAt(0)
            }
        }

        // Добавляем последний чанк
        if (currentChunk.isNotEmpty()) {
            chunks.add(
                TextChunk(
                    text = currentChunk.toString().trim(),
                    position = chunkPosition,
                    wordCount = currentWordCount
                )
            )
        }

        return chunks
    }

    /**
     * Подсчитывает количество слов.
     */
    private fun countWords(text: String): Int {
        return text.trim().split(WORD_PATTERN).size
    }
}

/**
 * Фрагмент текста.
 */
data class TextChunk(
    val text: String,
    val position: Int,      // Позиция чанка в документе
    val wordCount: Int
)
