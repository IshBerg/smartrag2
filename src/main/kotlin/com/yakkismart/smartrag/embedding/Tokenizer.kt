/**
 * SmartRAG v2 - Tokenizer
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Простой WordPiece токенизатор для BERT-подобных моделей.
 * Для production используйте HuggingFace Tokenizers.
 */
package com.yakkismart.smartrag.embedding

/**
 * Простой токенизатор.
 */
class Tokenizer(
    private val maxLength: Int = 512
) {

    companion object {
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"
        private const val UNK_TOKEN = "[UNK]"

        private const val CLS_TOKEN_ID = 101L
        private const val SEP_TOKEN_ID = 102L
        private const val PAD_TOKEN_ID = 0L
        private const val UNK_TOKEN_ID = 100L

        // Простой vocab для базовой токенизации
        // В production используйте vocab.txt из модели
        private val BASIC_VOCAB = mapOf(
            CLS_TOKEN to CLS_TOKEN_ID,
            SEP_TOKEN to SEP_TOKEN_ID,
            PAD_TOKEN to PAD_TOKEN_ID,
            UNK_TOKEN to UNK_TOKEN_ID
        )
    }

    /**
     * Токенизирует текст.
     * @return пары (input_ids, attention_mask)
     */
    fun tokenize(text: String): TokenizerOutput {
        // Простая токенизация по словам
        val words = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        // Ограничиваем длину
        val tokensCount = minOf(words.size + 2, maxLength) // +2 для CLS и SEP

        val inputIds = LongArray(maxLength) { PAD_TOKEN_ID }
        val attentionMask = LongArray(maxLength) { 0L }

        // [CLS]
        inputIds[0] = CLS_TOKEN_ID
        attentionMask[0] = 1L

        // Слова (используем простое хеширование для ID)
        var idx = 1
        words.take(tokensCount - 2).forEach { word ->
            inputIds[idx] = wordToId(word)
            attentionMask[idx] = 1L
            idx++
        }

        // [SEP]
        if (idx < maxLength) {
            inputIds[idx] = SEP_TOKEN_ID
            attentionMask[idx] = 1L
        }

        return TokenizerOutput(
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenCount = idx + 1
        )
    }

    /**
     * Токенизирует несколько текстов (batch).
     */
    fun tokenizeBatch(texts: List<String>): BatchTokenizerOutput {
        val outputs = texts.map { tokenize(it) }

        val batchSize = outputs.size
        val inputIds = Array(batchSize) { outputs[it].inputIds }
        val attentionMasks = Array(batchSize) { outputs[it].attentionMask }

        return BatchTokenizerOutput(
            inputIds = inputIds,
            attentionMask = attentionMasks
        )
    }

    /**
     * Конвертирует слово в ID.
     * Простая хеш-функция для демо.
     * В production используйте реальный vocab.
     */
    private fun wordToId(word: String): Long {
        return BASIC_VOCAB[word]
            ?: (word.hashCode().toLong() and 0x7FFFFFFF) % 30000 + 1000
    }
}

/**
 * Результат токенизации.
 */
data class TokenizerOutput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenizerOutput

        if (!inputIds.contentEquals(other.inputIds)) return false
        if (!attentionMask.contentEquals(other.attentionMask)) return false
        if (tokenCount != other.tokenCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenCount
        return result
    }
}

/**
 * Batch результат токенизации.
 */
data class BatchTokenizerOutput(
    val inputIds: Array<LongArray>,
    val attentionMask: Array<LongArray>
)
