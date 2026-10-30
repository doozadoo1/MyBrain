package com.mhss.app.data.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolBase
import ai.koog.serialization.typeToken
import com.mhss.app.data.rag.KnowledgeIndexer
import com.mhss.app.data.nowMillis
import com.mhss.app.domain.model.Bookmark
import com.mhss.app.domain.use_case.AddBookmarkUseCase
import com.mhss.app.domain.use_case.SearchBookmarksUseCase
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Factory
class BookmarkToolSet(
    private val addBookmark: AddBookmarkUseCase,
    private val searchBookmarksUseCase: SearchBookmarksUseCase,
    private val knowledgeIndexer: KnowledgeIndexer
) {
    private val createBookmarkTool = object : Tool<CreateBookmarkArgs, BookmarkIdResult>(
        argsType = typeToken<CreateBookmarkArgs>(),
        resultType = typeToken<BookmarkIdResult>(),
        name = CREATE_BOOKMARK_TOOL,
        description = "Create bookmark. Returns ID."
    ) {
        override suspend fun execute(args: CreateBookmarkArgs): BookmarkIdResult {
            val id = Uuid.generateV7().toString()
            val bookmark = Bookmark(
                url = args.url,
                title = args.title,
                description = args.description,
                createdDate = nowMillis(),
                updatedDate = nowMillis(),
                id = id
            )
            addBookmark(bookmark)
            runCatching { knowledgeIndexer.indexBookmark(bookmark) }
            return BookmarkIdResult(createdBookmarkId = id)
        }
    }

    private val searchBookmarksTool = object : Tool<SearchBookmarksArgs, SearchBookmarksResult>(
        argsType = typeToken<SearchBookmarksArgs>(),
        resultType = typeToken<SearchBookmarksResult>(),
        name = SEARCH_BOOKMARKS_TOOL,
        description = "Search bookmarks by title/description/URL (partial match)."
    ) {
        override suspend fun execute(args: SearchBookmarksArgs): SearchBookmarksResult =
            SearchBookmarksResult(searchBookmarksUseCase(args.query).map { it.toToolResult() })
    }

    val tools: List<ToolBase<*, *>> = listOf(createBookmarkTool, searchBookmarksTool)
}

@Serializable
data class CreateBookmarkArgs(
    val url: String,
    val title: String = "",
    val description: String = ""
)

@Serializable
data class SearchBookmarksArgs(val query: String)

@Serializable
data class BookmarkIdResult(val createdBookmarkId: String)

@Serializable
data class SearchBookmarksResult(val bookmarks: List<BookmarkToolResult>)
