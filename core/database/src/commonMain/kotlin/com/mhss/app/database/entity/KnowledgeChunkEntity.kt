package com.mhss.app.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One chunk of chunked user knowledge (a note, diary entry or bookmark).
 *
 * `embedding` holds the vector produced by the configured embedding provider
 * (byte-serialized little-endian FloatArray) and is null when embeddings are
 * unavailable - chunks are still usable for keyword (FTS) retrieval.
 */
@Entity(
    tableName = "knowledge_chunks",
    indices = [Index(value = ["source_id"])]
)
data class KnowledgeChunkEntity(
    @PrimaryKey
    val id: String,
    // One of KnowledgeSource slugs: "note", "diary_entry", "bookmark"
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    // ID of the source note / diary entry / bookmark
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,
    val title: String,
    val text: String,
    // updated_date of the source entity at indexing time, used for staleness detection
    @ColumnInfo(name = "source_updated_date")
    val sourceUpdatedDate: Long,
    val embedding: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeChunkEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Standalone FTS4 index over [KnowledgeChunkEntity] text.
 *
 * Room's FTS entities may only contain String fields, so the source chunk id
 * is duplicated here to allow deleting/lookup by id without docid juggling.
 */
@Fts4
@Entity(tableName = "knowledge_chunks_fts")
data class KnowledgeChunkFtsEntity(
    val id: String,
    val title: String,
    val text: String
)
