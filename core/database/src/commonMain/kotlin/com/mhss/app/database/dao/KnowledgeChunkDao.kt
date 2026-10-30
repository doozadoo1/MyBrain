package com.mhss.app.database.dao

import androidx.room3.Dao
import androidx.room3.ColumnInfo
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.mhss.app.database.entity.KnowledgeChunkEntity
import com.mhss.app.database.entity.KnowledgeChunkFtsEntity

@Dao
interface KnowledgeChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    // Must be called only after the previous rows for the same source were removed
    // (virtual FTS4 tables do not support REPLACE conflict resolution).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFtsRows(rows: List<KnowledgeChunkFtsEntity>)

    @Query("SELECT id FROM knowledge_chunks WHERE source_type = :sourceType AND source_id = :sourceId")
    suspend fun getChunkIdsBySource(sourceType: String, sourceId: String): List<String>

    @Query("DELETE FROM knowledge_chunks WHERE source_type = :sourceType AND source_id = :sourceId")
    suspend fun deleteChunksBySource(sourceType: String, sourceId: String)

    @Query("DELETE FROM knowledge_chunks WHERE id IN (:ids)")
    suspend fun deleteChunksByIds(ids: List<String>)

    @Query("DELETE FROM knowledge_chunks_fts WHERE id IN (:ids)")
    suspend fun deleteFtsByIds(ids: List<String>)

    /**
     * Atomically replaces all chunks of one source: removes old rows (both
     * tables), inserts the new ones and keeps the FTS table in sync.
     */
    @Transaction
    suspend fun replaceSourceChunks(
        sourceType: String,
        sourceId: String,
        chunks: List<KnowledgeChunkEntity>,
        ftsRows: List<KnowledgeChunkFtsEntity>
    ) {
        val oldIds = getChunkIdsBySource(sourceType, sourceId)
        if (oldIds.isNotEmpty()) {
            deleteFtsByIds(oldIds)
            deleteChunksByIds(oldIds)
        }
        if (chunks.isNotEmpty()) {
            insertChunks(chunks)
            insertFtsRows(ftsRows)
        }
    }

    @Query("DELETE FROM knowledge_chunks WHERE source_id = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("SELECT * FROM knowledge_chunks WHERE id = :id")
    suspend fun getChunk(id: String): KnowledgeChunkEntity?

    @Query("SELECT * FROM knowledge_chunks WHERE source_type = :sourceType AND source_id = :sourceId")
    suspend fun getChunksBySource(sourceType: String, sourceId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE id IN (:ids)")
    suspend fun getChunksByIds(ids: List<String>): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE embedding IS NOT NULL")
    suspend fun getEmbeddedChunks(): List<KnowledgeChunkEntity>

    @Query(
        "SELECT * FROM knowledge_chunks_fts WHERE knowledge_chunks_fts MATCH :query LIMIT :limit"
    )
    suspend fun searchFts(query: String, limit: Int): List<KnowledgeChunkFtsEntity>

    @Query("DELETE FROM knowledge_chunks")
    suspend fun deleteAllChunks()

    @Query("DELETE FROM knowledge_chunks_fts")
    suspend fun deleteAllFts()

    @Transaction
    suspend fun clearAll() {
        deleteAllFts()
        deleteAllChunks()
    }

    @Query("SELECT COUNT(*) FROM knowledge_chunks")
    suspend fun countChunks(): Long

    @Query("SELECT COUNT(*) FROM knowledge_chunks WHERE embedding IS NOT NULL")
    suspend fun countEmbedded(): Long

    @Query(
        "SELECT source_id, source_updated_date FROM knowledge_chunks WHERE source_type = :sourceType GROUP BY source_id"
    )
    suspend fun getIndexedSources(sourceType: String): List<IndexedSourceState>
}

data class IndexedSourceState(
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "source_updated_date")
    val sourceUpdatedDate: Long
)
