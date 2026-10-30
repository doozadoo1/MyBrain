package com.mhss.app.data.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.mhss.app.data.rag.KnowledgeRetriever
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Factory

/**
 * RAG tool: semantic + keyword search across the user's notes, diary entries
 * and bookmarks. Returns full (chunk) text instead of the truncated previews
 * the other search tools return.
 */
@Factory
class KnowledgeToolSet(
    private val retriever: KnowledgeRetriever
) {

    private val retrieveKnowledgeTool = object : Tool<RetrieveKnowledgeArgs, RetrieveKnowledgeResult>(
        argsType = typeToken<RetrieveKnowledgeArgs>(),
        resultType = typeToken<RetrieveKnowledgeResult>(),
        name = RETRIEVE_KNOWLEDGE_TOOL,
        description =
            "Search the user's personal knowledge (notes, diary entries, bookmarks) with natural " +
                "language. Combines exact keyword matching with semantic/similarity search. " +
                "ALWAYS use this tool for any question about the user's own content, notes, memories, " +
                "things they wrote or saved, even when the exact wording is unknown. " +
                "If an entry is marked stale=true, fetch the full source with " +
                "$GET_NOTE_BY_ID_TOOL/$GET_DIARY_ENTRY_TOOL to see current content."
    ) {
        override suspend fun execute(
            args: RetrieveKnowledgeArgs
        ): RetrieveKnowledgeResult {
            val hits = retriever.retrieve(
                query = args.query,
                topK = args.maxResults ?: com.mhss.app.data.rag.KnowledgeSettings.DEFAULT_TOP_K
            )
            return RetrieveKnowledgeResult(
                hits = hits.map { hit ->
                    KnowledgeHitResult(
                        type = hit.type,
                        id = hit.sourceId,
                        title = hit.title,
                        text = hit.text,
                        date = hit.date,
                        url = hit.url,
                        stale = hit.stale
                    )
                }
            )
        }
    }

    val tools: List<ToolBase<*, *>> = listOf(retrieveKnowledgeTool)
}

@Serializable
data class RetrieveKnowledgeArgs(
    @property:LLMDescription(
        "Natural language search query describing the information to find, e.g. " +
            "'what did I write about the sleep consultant' or 'notes about project deadline'"
    )
    val query: String,
    @property:LLMDescription("Optional: max number of results (1-10). Default 6.")
    val maxResults: Int? = null
)

@Serializable
data class KnowledgeHitResult(
    val type: String,
    val id: String,
    val title: String,
    val text: String,
    val date: String,
    val url: String? = null,
    val stale: Boolean = false
)

@Serializable
data class RetrieveKnowledgeResult(val hits: List<KnowledgeHitResult>)
