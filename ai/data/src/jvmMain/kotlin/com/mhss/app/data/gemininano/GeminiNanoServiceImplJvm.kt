package com.mhss.app.data.gemininano

import com.mhss.app.data.nano.GeminiNanoException
import com.mhss.app.domain.gemininano.GeminiNanoMode
import com.mhss.app.domain.gemininano.GeminiNanoService
import com.mhss.app.domain.gemininano.GeminiNanoStatus
import com.mhss.app.domain.model.AiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single

/**
 * Desktop stub of [GeminiNanoService]: Gemini Nano (via the mlkit genai
 * client) is Android-only, so desktop reports the feature as unsupported and
 * every request fails fast with a [GeminiNanoException].
 */
@Single(binds = [GeminiNanoService::class])
class GeminiNanoServiceImplJvm : GeminiNanoService {

    private val _status = MutableStateFlow<GeminiNanoStatus>(
        GeminiNanoStatus.Unsupported("Gemini Nano is only available on supported Android devices")
    )
    override val status: StateFlow<GeminiNanoStatus> = _status.asStateFlow()

    private fun unsupported(): GeminiNanoException =
        GeminiNanoException("Gemini Nano is not available on desktop")

    override suspend fun warmup(mode: GeminiNanoMode) { /* unsupported on desktop */ }

    override suspend fun refreshStatus(mode: GeminiNanoMode) {
        _status.value = GeminiNanoStatus.Unsupported("Gemini Nano is only available on supported Android devices")
    }

    override fun download(mode: GeminiNanoMode) { /* nothing to download on desktop */ }

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        systemMessage: String,
        mode: GeminiNanoMode
    ): String {
        throw unsupported()
    }

    override fun sendPrompt(
        prompt: String,
        mode: GeminiNanoMode
    ): Flow<String> = flow<String> { throw unsupported() }
}
