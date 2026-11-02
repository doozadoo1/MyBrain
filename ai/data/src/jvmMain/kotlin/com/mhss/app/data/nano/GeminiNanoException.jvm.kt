package com.mhss.app.data.nano

import com.mhss.app.domain.model.AssistantResult

actual class GeminiNanoException(message: String?) : Exception(message)

actual fun GeminiNanoException.toAssistantResult(): AssistantResult.Failure {
    return AssistantResult.OtherError("Gemini Nano Error: ${this.message ?: "Unknown error"}")
}
