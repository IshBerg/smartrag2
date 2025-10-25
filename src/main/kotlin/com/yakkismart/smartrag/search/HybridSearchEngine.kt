/**
 * SmartRAG v2 - Hybrid Search Engine
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 *
 * Объединяет несколько стратегий поиска с весами.
 * Использует Reciprocal Rank Fusion для объединения результатов.
 */
package com.yakkismart.smartrag.search

import com.yakkismart.smartrag.api.SearchParams
import com.yakkismart.smartrag.api.SearchResult
import com.yakkismart.smartrag.api.SearchResultItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Гибридный поисковый движок.
 */
class HybridSearchEngine(
    private val strategies: List<Pair<SearchStrategy, Float>>  // (стратегия, вес)
) : SearchStrategy {

    override val name: String = "HybridSearch"

    companion object {
        private const val RRF_K = 60  // Константа для Reciprocal Rank Fusion
    }

    override suspend fun search(
        query: String,
        params: SearchParams
    ): Result<SearchResult> = coroutineScope {
        val startTime = System.currentTimeMillis()

        try {
            // Запускаем все стратегии параллельно
            val results = strategies.map { (strategy, weight) ->
                async {
                    strategy.search(query, params).getOrNull()?.let { result ->
                        Triple(result, weight, strategy.name)
                    }
                }
            }.awaitAll().filterNotNull()

            if (results.isEmpty()) {
                return@coroutineScope Result.success(
                    SearchResult(
                        items = emptyList(),
                        totalCount = 0,
                        searchTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            }

            // Reciprocal Rank Fusion для объединения результатов
            val fusedScores = mutableMapOf<Long, FusionScore>()

            results.forEach { (result, weight, strategyName) ->
                result.items.forEachIndexed { rank, item ->
                    val rrfScore = weight / (RRF_K + rank + 1)

                    fusedScores.merge(
                        item.id,
                        FusionScore(item, rrfScore, mutableMapOf(strategyName to item.score))
                    ) { existing, new ->
                        existing.copy(
                            fusedScore = existing.fusedScore + new.fusedScore,
                            strategyScores = existing.strategyScores.apply {
                                put(strategyName, item.score)
                            }
                        )
                    }
                }
            }

            // Сортируем по объединенному score
            val sortedItems = fusedScores.values
                .sortedByDescending { it.fusedScore }
                .take(params.limit)
                .map { fusion ->
                    SearchResultItem(
                        id = fusion.item.id,
                        text = fusion.item.text,
                        score = fusion.fusedScore,
                        metadata = fusion.item.metadata + mapOf(
                            "fusion_score" to fusion.fusedScore,
                            "strategies" to fusion.strategyScores
                        )
                    )
                }
                .filter { it.score >= params.minScore }

            Result.success(
                SearchResult(
                    items = sortedItems,
                    totalCount = sortedItems.size,
                    searchTimeMs = System.currentTimeMillis() - startTime
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isApplicable(query: String): Float = 1.0f  // Всегда применим

    /**
     * Промежуточная структура для Fusion.
     */
    private data class FusionScore(
        val item: SearchResultItem,
        val fusedScore: Float,
        val strategyScores: MutableMap<String, Float>
    )
}
