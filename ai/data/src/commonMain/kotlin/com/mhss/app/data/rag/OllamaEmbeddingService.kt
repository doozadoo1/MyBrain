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
 * Embeddings via a local or remote Ollama server (`POST /api/embed`).
 * Reuses the Ollama base URL configured for the chat AI provider, so users
 * configure their server once.
 */
@Single
class OllamaEmbeddingService(
    private val getPreference: GetPreferenceUseCase,
    private val settings: KnowledgeSettings
) : EmbeddingService {

    private val json = Json { encodeDefaults = true }

    @Serializable
    internal data class EmbedRequest(val model: String, val input: List<String>)

    @Serializable
    internal data class EmbedResponse(val embeddings: List<List<Float>> = emptyList())

    override suspend fun embed(texts: List<String>): List<List<Float>>? = runCatching {
        val baseUrl = resolveBaseUrl()
        if (baseUrl.isBlank()) return null
        HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }.use { client ->
            val response = client.post("${baseUrl.trimEnd('/')}/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        EmbedRequest(model = settings.embeddingModel(), input = texts)
                    )
                )
            }
            val body = response.bodyAsText()
            json.decodeFromString<EmbedResponse>(body).embeddings
        }
    }.getOrNull()

    private suspend fun resolveBaseUrl(): String {
        val customUrl = getPreference(
            stringPreferencesKey(AiProvider.Ollama.customUrlPref.orEmpty()),
            ""
        ).first()
        return customUrl.ifBlank { AiProvider.Ollama.defaultBaseUrl.orEmpty() }
    }
}

private inline fun <T> HttpClient.use(block: (HttpClient) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
