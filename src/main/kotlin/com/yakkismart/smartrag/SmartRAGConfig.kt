/**
 * SmartRAG v2 - Configuration
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
package com.yakkismart.smartrag

data class SmartRAGConfig(
    val dbName: String = "smartrag.db",
    val vectorBackend: VectorBackend = VectorBackend.SQLITE_FALLBACK,
    val modelPath: String = "models/all-MiniLM-L6-v2.onnx",
    val embeddingDimensions: Int = 384,
    val maxVectorElements: Int = 1_000_000
) {
    companion object {
        fun default() = SmartRAGConfig()
    }
}

enum class VectorBackend {
    RUST,
    SQLITE_FALLBACK
}
