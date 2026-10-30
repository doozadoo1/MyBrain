package com.mhss.app.data.rag

import org.koin.core.annotation.Single

/**
 * Picks the configured [EmbeddingService] based on user preferences.
 * Returns null when the index is set to keyword-only mode.
 */
@Single
class EmbeddingServiceResolver(
    private val settings: KnowledgeSettings,
    private val ollamaEmbeddingService: OllamaEmbeddingService,
    private val openAiCompatibleEmbeddingService: OpenAiCompatibleEmbeddingService
) {
    suspend fun resolve(): EmbeddingService? = when (settings.embeddingProvider()) {
        KnowledgeEmbeddingProvider.NONE -> null
        KnowledgeEmbeddingProvider.OLLAMA -> ollamaEmbeddingService
        KnowledgeEmbeddingProvider.OPENAI_COMPATIBLE -> openAiCompatibleEmbeddingService
    }
}
