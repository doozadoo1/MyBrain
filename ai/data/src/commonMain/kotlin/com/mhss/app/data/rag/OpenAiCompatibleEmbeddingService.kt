package com.mhss.app.data.rag

import com.mhss.app.preferences.domain.model.AiProvider
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * Embeddings via any OpenAI-compatible `/v1/embeddings` endpoint (LM Studio,
 * llama.cpp server, xinference, ...). Reuses the LM Studio base URL configured
 * for the chat AI provider.
 */
@Single
class OpenAiCompatibleEmbeddingService(
    private val getPreference: GetPreferenceUseCase,
    private val settings: KnowledgeSettings
) : EmbeddingService {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    internal data class EmbedRequest(val model: String, val input: List<String>)

    @Serializable
    internal data class EmbedItem(val index: Int = 0, val embedding: List<Float> = emptyList())

    @Serializable
    internal data class EmbedResponse(val data: List<EmbedItem> = emptyList())

    override suspend fun embed(texts: List<String>): List<List<Float>>? = runCatching {
        val baseUrl = resolveBaseUrl()
        if (baseUrl.isBlank()) return null
        val path = baseUrl.trimEnd('/').let {
            if (!it.endsWith("/v1")) "$it/v1" else it
        } + "/embeddings"
        HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }.use { client ->
            val response = client.post(path) {
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        EmbedRequest(model = settings.embeddingModel(), input = texts)
                    )
                )
            }
            val body = response.bodyAsText()
            json.decodeFromString<EmbedResponse>(body)
                .data
                .sortedBy { it.index }
                .map { it.embedding }
        }
    }.getOrNull()

    private suspend fun resolveBaseUrl(): String {
        val customUrl = getPreference(
            stringPreferencesKey(AiProvider.LmStudio.customUrlPref.orEmpty()),
            ""
        ).first()
        return customUrl.ifBlank { AiProvider.LmStudio.defaultBaseUrl.orEmpty() }
    }
}

private inline fun <T> HttpClient.use(block: (HttpClient) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
