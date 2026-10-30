package com.mhss.app.data.rag

/** The user knowledge types covered by the RAG index. */
enum class KnowledgeSource(val id: String, val displayName: String) {
    NOTE("note", "Note"),
    DIARY_ENTRY("diary_entry", "Diary entry"),
    BOOKMARK("bookmark", "Bookmark")
}
