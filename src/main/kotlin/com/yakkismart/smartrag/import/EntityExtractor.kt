/**
 * SmartRAG v2 - Entity Extractor
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Извлекает сущности из текста (NER).
 * Простая rule-based реализация без ML.
 */
package com.yakkismart.smartrag.import

/**
 * Extractor сущностей.
 */
class EntityExtractor {

    companion object {
        // Паттерны для извлечения
        private val EMAIL_PATTERN = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")
        private val PHONE_PATTERN = Regex("""\+?\d{1,3}?[-.\s]?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}""")
        private val URL_PATTERN = Regex("""https?://[^\s]+""")
        private val MENTION_PATTERN = Regex("""@(\w+)""")
        private val HASHTAG_PATTERN = Regex("""#(\w+)""")

        // Слова-маркеры для организаций
        private val ORG_SUFFIXES = setOf(
            "Inc", "LLC", "Ltd", "Corp", "Corporation", "Company", "Co",
            "Foundation", "Institute", "University", "College", "School"
        )

        // Слова-маркеры для локаций
        private val LOCATION_MARKERS = setOf(
            "Street", "Avenue", "Road", "Boulevard", "City", "Town",
            "Country", "State", "Province", "District"
        )
    }

    /**
     * Извлекает все сущности из текста.
     */
    fun extract(text: String): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Email
        EMAIL_PATTERN.findAll(text).forEach { match ->
            entities.add(
                Entity(
                    type = EntityType.EMAIL,
                    value = match.value,
                    position = match.range.first,
                    context = extractContext(text, match.range)
                )
            )
        }

        // Phone
        PHONE_PATTERN.findAll(text).forEach { match ->
            entities.add(
                Entity(
                    type = EntityType.PHONE,
                    value = match.value,
                    position = match.range.first,
                    context = extractContext(text, match.range)
                )
            )
        }

        // URL
        URL_PATTERN.findAll(text).forEach { match ->
            entities.add(
                Entity(
                    type = EntityType.URL,
                    value = match.value,
                    position = match.range.first,
                    context = extractContext(text, match.range)
                )
            )
        }

        // Mentions (@username)
        MENTION_PATTERN.findAll(text).forEach { match ->
            entities.add(
                Entity(
                    type = EntityType.PERSON,
                    value = match.groupValues[1],
                    position = match.range.first,
                    context = extractContext(text, match.range)
                )
            )
        }

        // Hashtags
        HASHTAG_PATTERN.findAll(text).forEach { match ->
            entities.add(
                Entity(
                    type = EntityType.TOPIC,
                    value = match.groupValues[1],
                    position = match.range.first,
                    context = extractContext(text, match.range)
                )
            )
        }

        // Proper nouns (прописные буквы)
        entities.addAll(extractProperNouns(text))

        return entities
    }

    /**
     * Извлекает имена собственные (Capitalized Words).
     */
    private fun extractProperNouns(text: String): List<Entity> {
        val entities = mutableListOf<Entity>()
        val words = text.split(Regex("\\s+"))

        var i = 0
        while (i < words.size) {
            val word = words[i].trim { it.isLetterOrDigit().not() }

            if (word.length > 1 && word.first().isUpperCase()) {
                // Проверяем следующие слова
                val phrase = mutableListOf(word)
                var j = i + 1

                while (j < words.size && j < i + 5) {
                    val nextWord = words[j].trim { it.isLetterOrDigit().not() }
                    if (nextWord.length > 1 && nextWord.first().isUpperCase()) {
                        phrase.add(nextWord)
                        j++
                    } else {
                        break
                    }
                }

                val fullPhrase = phrase.joinToString(" ")

                // Определяем тип сущности
                val type = when {
                    phrase.any { it in ORG_SUFFIXES } -> EntityType.ORGANIZATION
                    phrase.any { it in LOCATION_MARKERS } -> EntityType.LOCATION
                    phrase.size == 1 && word.length > 2 -> EntityType.PERSON
                    phrase.size >= 2 -> EntityType.PERSON
                    else -> null
                }

                if (type != null) {
                    val position = text.indexOf(fullPhrase)
                    if (position >= 0) {
                        entities.add(
                            Entity(
                                type = type,
                                value = fullPhrase,
                                position = position,
                                context = extractContext(text, position until position + fullPhrase.length)
                            )
                        )
                    }
                }

                i = j
            } else {
                i++
            }
        }

        return entities
    }

    /**
     * Извлекает контекст вокруг сущности.
     */
    private fun extractContext(text: String, range: IntRange): String {
        val start = maxOf(0, range.first - 50)
        val end = minOf(text.length, range.last + 50)
        return text.substring(start, end).trim()
    }
}

/**
 * Типы сущностей.
 */
enum class EntityType {
    PERSON,
    ORGANIZATION,
    LOCATION,
    EMAIL,
    PHONE,
    URL,
    TOPIC,
    DATE,
    MONEY
}

/**
 * Извлеченная сущность.
 */
data class Entity(
    val type: EntityType,
    val value: String,
    val position: Int,
    val context: String,
    val confidence: Float = 1.0f
)
