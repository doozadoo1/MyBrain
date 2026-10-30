package com.mhss.app.data.repository

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import com.mhss.app.data.EmptyAiClient
import com.mhss.app.data.buildChatPrompt
import com.mhss.app.data.buildChatSystemMessage
import com.mhss.app.data.nano.GeminiNanoException
import com.mhss.app.data.nano.toAssistantResult
import com.mhss.app.data.nowMillis
import com.mhss.app.data.toAiMessage
import com.mhss.app.data.toLLModel
import com.mhss.app.data.toNewAssistantMessage
import com.mhss.app.data.rag.KnowledgeRetriever
import com.mhss.app.data.rag.KnowledgeIndexWorker
import com.mhss.app.data.rag.KnowledgeSettings
import com.mhss.app.data.tools.BookmarkToolSet
import com.mhss.app.data.tools.CalendarToolSet
import com.mhss.app.data.tools.DiaryToolSet
import com.mhss.app.data.tools.KnowledgeToolSet
import com.mhss.app.data.tools.NoteToolSet
import com.mhss.app.data.tools.TaskToolSet
import com.mhss.app.domain.MAX_CONSECUTIVE_TOOL_CALLS
import com.mhss.app.domain.gemininano.GeminiNanoService
import com.mhss.app.domain.gemininano.toGeminiNanoMode
import com.mhss.app.domain.knowledgeContextHeader
import com.mhss.app.domain.model.AiMessage
import com.mhss.app.domain.model.AiRepositoryException
import com.mhss.app.domain.model.AssistantResult
import com.mhss.app.domain.repository.AiRepository
import com.mhss.app.preferences.PrefsConstants.AI_PROVIDER_KEY
import com.mhss.app.preferences.PrefsConstants.AI_TOOLS_ENABLED_KEY
import com.mhss.app.preferences.domain.model.AiProvider
import com.mhss.app.preferences.domain.model.booleanPreferencesKey
import com.mhss.app.preferences.domain.model.intPreferencesKey
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.model.toAiProvider
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Factory
class AiRepositoryImpl(
    private val getPreferenceUseCase: GetPreferenceUseCase,
    @Named("applicationScope") private val applicationScope: CoroutineScope,
    private val noteToolSet: NoteToolSet,
    private val taskToolSet: TaskToolSet,
    private val calendarToolSet: CalendarToolSet,
    private val diaryToolSet: DiaryToolSet,
    private val bookmarkToolSet: BookmarkToolSet,
    private val knowledgeToolSet: KnowledgeToolSet,
    private val knowledgeRetriever: KnowledgeRetriever,
    private val knowledgeIndexWorker: KnowledgeIndexWorker,
    private val toolExecutor: AiToolExecutor,
    private val geminiNanoService: GeminiNanoService
) : AiRepository {
    private val toolRegistry = ToolRegistry {
        tools(noteToolSet.tools)
        tools(taskToolSet.tools)
        tools(calendarToolSet.tools)
        tools(diaryToolSet.tools)
        tools(bookmarkToolSet.tools)
        tools(knowledgeToolSet.tools)
    }
    private val toolDescriptors = toolRegistry.tools.map { it.descriptor }
    private var chatSystemMessage = ""
    private var llmExecutor: PromptExecutor? = null
    private var llModel: LLModel? = null
    private var toolsEnabled: Boolean = false
    private var selectedProvider: AiProvider = AiProvider.None
    private var geminiNanoModel: String = AiProvider.GeminiNano.defaultModel.orEmpty()

    init {
        applicationScope.launch {
            configureAiClient()
        }
        // Keep the knowledge index in sync with notes/diary/bookmarks changes
        // (UI writes, AI tool writes and sync merges). Runs as long as this
        // repository instance lives.
        applicationScope.launch {
            knowledgeIndexWorker.observeForever()
        }
    }

    override fun sendPrompt(prompt: String): Flow<String> = flow {
        if (selectedProvider == AiProvider.GeminiNano) {
            try {
                geminiNanoService.sendPrompt(
                    prompt = prompt,
                    mode = geminiNanoModel.toGeminiNanoMode()
                ).collect { chunk ->
                    emit(chunk)
                }
            } catch (e: GeminiNanoException) {
                throw AiRepositoryException(e.toAssistantResult())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                throw AiRepositoryException(AssistantResult.OtherError(e.message))
            }
            return@flow
        }

        val client = llmExecutor ?: throw AiRepositoryException(AssistantResult.OtherError("AI Client not initialized"))
        val model = llModel ?: throw AiRepositoryException(AssistantResult.OtherError("Model not selected"))

        val llmPrompt = prompt("user_prompt", LLMParams()) {
            user(prompt)
        }

        try {
            client.executeStreaming(prompt = llmPrompt, model = model).collect { frame ->
                if (frame is StreamFrame.TextDelta) emit(frame.text)
            }
        } catch (e: LLMClientException) {
            throw AiRepositoryException(AssistantResult.OtherError(e.message))
        } catch (e: IOException) {
            e.printStackTrace()
            throw AiRepositoryException(AssistantResult.InternetError)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            throw AiRepositoryException(AssistantResult.OtherError(e.message))
        }
    }.flowOn(Dispatchers.IO).batchStream()

    @OptIn(InternalAgentToolsApi::class, ExperimentalUuidApi::class)
    override fun sendMessage(messages: List<AiMessage>): Flow<AiMessage> = flow {
        if (selectedProvider == AiProvider.GeminiNano) {
            try {
                val result = geminiNanoService.sendMessage(
                    messages = messages,
                    systemMessage = awaitInjectedSystemMessage(messages, allowInjection = true),
                    mode = geminiNanoModel.toGeminiNanoMode()
                )
                emit(
                    AiMessage.AssistantMessage(
                        uuid = Uuid.generateV7().toString(),
                        content = result,
                        time = nowMillis()
                    )
                )
            } catch (e: GeminiNanoException) {
                throw AiRepositoryException(e.toAssistantResult())
            } catch (e: Exception) {
                e.printStackTrace()
                throw AiRepositoryException(AssistantResult.OtherError(e.message))
            }
            return@flow
        }

        val model =
            llModel ?: throw AiRepositoryException(AssistantResult.OtherError("Model not selected"))
        val executor = llmExecutor
            ?: throw AiRepositoryException(AssistantResult.OtherError("AI Client not initialized"))

        var currentMessages = messages
        var consecutiveToolCalls = 0

        val systemMessage = awaitInjectedSystemMessage(messages, allowInjection = !toolsEnabled)

        try {
            do {
                if (consecutiveToolCalls >= MAX_CONSECUTIVE_TOOL_CALLS) {
                    throw AiRepositoryException(AssistantResult.ToolCallLimitExceeded)
                }

                val result = executor.execute(
                    prompt = currentMessages.buildChatPrompt(systemMessage),
                    model = model,
                    tools = if (toolsEnabled) toolDescriptors else emptyList()
                )

                val toolCalls = result.parts.filterIsInstance<MessagePart.Tool.Call>()
                val assistantMessage = result
                    .takeIf { it.textContent().isNotBlank() }
                    ?.toNewAssistantMessage()

                if (toolCalls.isEmpty()) {
                    assistantMessage?.let { emit(it) }
                    break
                }

                consecutiveToolCalls++

                val thoughtSignatures = toolExecutor.extractThoughtSignatures(result.parts)
                val toolCallMessages = toolCalls.map { toolCall ->
                    val toolCallMessageResult = toolExecutor.executeToolCall(toolCall, toolRegistry)
                    toolCall.toAiMessage(toolCallMessageResult, thoughtSignatures[toolCall]).also {
                        emit(it)
                    }
                }

                currentMessages = currentMessages + toolCallMessages

                if (assistantMessage != null) {
                    emit(assistantMessage)
                    currentMessages = currentMessages + assistantMessage
                }

            } while (toolCalls.isNotEmpty())
        } catch (e: AiRepositoryException) {
            throw e
        } catch (e: LLMClientException) {
            e.printStackTrace()
            throw AiRepositoryException(AssistantResult.OtherError(e.message))
        } catch (e: IOException) {
            e.printStackTrace()
            throw AiRepositoryException(AssistantResult.InternetError)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            throw AiRepositoryException(AssistantResult.OtherError(e.message))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Builds the system message with retrieved knowledge injected when the
     * model cannot use tools (e.g. Gemini Nano) or tools are disabled.
     * With tools enabled the model reaches the same knowledge via the
     * retrieveKnowledge tool instead.
     */
    private suspend fun awaitInjectedSystemMessage(
        messages: List<AiMessage>,
        allowInjection: Boolean
    ): String {
        val base = chatSystemMessage.ifBlank { buildChatSystemMessage(false) }
        if (!allowInjection) return base
        val query = messages.lastOrNull { it is AiMessage.UserMessage }
            ?.let { it as AiMessage.UserMessage }?.content ?: return base
        val injection = buildKnowledgeContext(query) ?: return base
        return "$base\n\n$injection"
    }

    private suspend fun buildKnowledgeContext(query: String): String? = runCatching {
        val hits = knowledgeRetriever.retrieve(query, topK = INJECTED_TOP_K)
        if (hits.isEmpty()) return null
        val context = buildString {
            append(knowledgeContextHeader)
            for (hit in hits) {
                if (length >= KnowledgeSettings.MAX_INJECTION_LENGTH) break
                appendLine()
                append("- [").append(hit.type).append("] ")
                if (hit.title.isNotBlank()) {
                    append(hit.title).append(" ")
                }
                append('(').append(hit.date).append("): ")
                append(hit.text)
                if (hit.stale) append(" (content may be outdated)")
            }
        }
        context.take(KnowledgeSettings.MAX_INJECTION_LENGTH) +
            "\nUse this knowledge when answering the user. Say so if the answer relies on it."
    }.getOrNull()

    private suspend fun configureAiClient() {
        val aiProvider = getPreferenceUseCase(
            intPreferencesKey(AI_PROVIDER_KEY),
            AiProvider.None.id
        ).first().toAiProvider()

        if (aiProvider == AiProvider.None) {
            llmExecutor = null
            llModel = null
            chatSystemMessage = ""
            return
        }

        val toolsEnabledPreferenceValue = getPreferenceUseCase(
            booleanPreferencesKey(AI_TOOLS_ENABLED_KEY),
            false
        ).first()

        selectedProvider = aiProvider
        toolsEnabled = toolsEnabledPreferenceValue && aiProvider != AiProvider.GeminiNano

        val model = getPreferenceUseCase(
            stringPreferencesKey(aiProvider.modelPref ?: ""),
            aiProvider.defaultModel.orEmpty()
        ).first()

        if (aiProvider == AiProvider.GeminiNano) {
            geminiNanoModel = model
            llmExecutor = null
            llModel = null
            chatSystemMessage = buildChatSystemMessage(false)
            geminiNanoService.warmup(geminiNanoModel.toGeminiNanoMode())
            return
        }

        val key = getPreferenceUseCase(
            stringPreferencesKey(aiProvider.keyPref ?: "none"),
            ""
        ).first()

        val customUrlPref = aiProvider.customUrlPref
        val customUrl = if (aiProvider.supportsCustomUrl && customUrlPref != null) {
            getPreferenceUseCase(
                stringPreferencesKey(customUrlPref),
                ""
            ).first()
        } else {
            ""
        }

        llModel = if (model.isNotBlank()) {
            model.toLLModel(aiProvider, withTools = toolsEnabled)
        } else {
            null
        }

        llmExecutor = llModel?.let {
            aiProvider.getExecutor(key, customUrl, it)
        }

        chatSystemMessage = buildChatSystemMessage(toolsEnabled)
    }

}

private val koogHttpClientFactory by lazy {
    KtorKoogHttpClient.Factory(baseClient = HttpClient(CIO))
}

private const val INJECTED_TOP_K = 4

private fun AiProvider.getExecutor(key: String, customUrl: String, llModel: LLModel): PromptExecutor {
    val client = when (this) {
        AiProvider.OpenAI -> OpenAILLMClient(
            apiKey = key,
            settings = if (customUrl.isBlank()) OpenAIClientSettings() else OpenAIClientSettings(baseUrl = customUrl),
            httpClientFactory = koogHttpClientFactory
        )
        AiProvider.Gemini -> GoogleLLMClient(
            apiKey = key,
            httpClientFactory = koogHttpClientFactory
        )
        AiProvider.Anthropic -> AnthropicLLMClient(
            apiKey = key,
            settings = AnthropicClientSettings(
                modelVersionsMap = mapOf(llModel to llModel.id)
            ),
            httpClientFactory = koogHttpClientFactory
        )
        AiProvider.OpenRouter -> OpenRouterLLMClient(
            apiKey = key,
            httpClientFactory = koogHttpClientFactory
        )
        AiProvider.Ollama -> if (customUrl.isBlank()) {
            OllamaClient(httpClientFactory = koogHttpClientFactory)
        } else {
            OllamaClient(
                httpClientFactory = koogHttpClientFactory,
                baseUrl = customUrl
            )
        }
        AiProvider.LmStudio -> OpenAILLMClient(
            apiKey = "",
            settings = OpenAIClientSettings(baseUrl = customUrl),
            httpClientFactory = koogHttpClientFactory
        )
        AiProvider.GeminiNano -> EmptyAiClient
        AiProvider.None -> EmptyAiClient
    }
    return MultiLLMPromptExecutor(client)
}

private fun Flow<String>.batchStream(durationMillis: Long = 150L): Flow<String> = flow {
    val accumulated = StringBuilder()
    var lastEmitTime = 0L
    collect { chunk ->
        accumulated.append(chunk)
        val currentTime = nowMillis()
        if (currentTime - lastEmitTime >= durationMillis) {
            emit(accumulated.toString())
            accumulated.clear()
            lastEmitTime = currentTime
        }
    }
    if (accumulated.isNotEmpty()) {
        emit(accumulated.toString())
    }
}
