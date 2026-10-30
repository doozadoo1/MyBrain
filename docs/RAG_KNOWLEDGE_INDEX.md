# Knowledge Index & RAG (prototype)

Adds retrieval-augmented generation over **notes + diary entries + bookmarks** so the
assistant can actually *find* things by meaning, not just exact keywords, and so
fully-local models (Gemini Nano / Ollama / LM Studio) get useful context.

## What was added

### Database (schema bump 7 → 8)
- `knowledge_chunks` (`KnowledgeChunkEntity`) — one row per text chunk of a source
  (note / diary entry / bookmark), with optional BLOB embedding (little-endian floats).
- `knowledge_chunks_fts` (`KnowledgeChunkFtsEntity`, FTS4) — standalone full-text index
  (`id`, `title`, `text`) kept in sync transactionally by `KnowledgeChunkDao.replaceSourceChunks`.
- `MIGRATION_7_8` creates both tables (see `RoomMigrations.kt`), registered in
  `DatabaseModule`. **First Gradle build after this change re-exports
  `core/database/schemas/...8.json` via KSP — commit it.**

Common core modules extended with light projection queries used by the diff indexer:
`NoteDao.getNoteIndexStates`, `DiaryDao.getEntryIndexStates`, `BookmarkDao.getBookmarkIndexStates`.

### RAG engine (`ai/data/src/commonMain/kotlin/com/mhss/app/data/rag/`)
| File | Role |
|---|---|
| `TextChunker` | Paragraph/heading-aware chunking (~1000 chars, 120 overlap), title prepended to every chunk |
| `EmbeddingService` | Provider contract: `embed(texts): List<List<Float>>?` (null = unavailable, never throws) |
| `OllamaEmbeddingService` | `POST {ollama}/api/embed`, reuses the existing Ollama base URL preference |
| `OpenAiCompatibleEmbeddingService` | `POST {lmstudio}/v1/embeddings`, reuses the existing LM Studio URL preference |
| `OnDeviceEmbeddingService` | placeholder for Phase 3 (on-device ONNX MiniLM); returns null for now |
| `EmbeddingServiceResolver` | picks the provider from prefs |
| `KnowledgeIndexer` | chunk → embed (reuse unchanged vectors) → store; `indexNote/indexDiaryEntry/indexBookmark`, `remove*`, `indexOutdated()`, `reindexAll()`, `clearAll()` |
| `KnowledgeIndexWorker` | observes `LocalChangeObserver` and runs `indexOutdated()` after every (coalesced) DB write |
| `KnowledgeRetriever` | hybrid retrieval: FTS4 BM25 + cosine similarity fused with Reciprocal Rank Fusion; validates candidates against live sources (deleted → dropped, changed → `stale=true`) |

### Assistant integration
- New tool **`retrieveKnowledge`** (`KnowledgeToolSet`, registered in `AiRepositoryImpl`'s
  registry and announced in `toolsSystemMessage`): returns up to N full-text hits with
  type, id, date, url, `stale` flag.
- **Context injection**: when the model has no tools (Gemini Nano) or tools are disabled,
  `AiRepositoryImpl.awaitInjectedSystemMessage()` retrieves top-4 chunks for the last user
  message and injects them into the system message. This is what makes Gemini Nano useful
  for "what did I write about …" questions.
- Notes/diary/bookmarks **created by the assistant via existing tools are indexed
  immediately** (create hooks in the three tool sets).

## Enabling semantic retrieval

The index works keyword-only (FTS) out of the box. For semantic recall set:

- `knowledge_embedding_provider`: `0` none, `1` Ollama, `2` OpenAI-compatible (LM Studio)
- `knowledge_embedding_model`: default `nomic-embed-text`
  - Ollama: `ollama pull nomic-embed-text`
  - LM Studio: download any embedding model served under `/v1/embeddings`

Both providers reuse the base URLs already configurable for chat (Settings → AI providers),
so there is nothing extra to configure beyond the model itself.

## Behavior notes

- Indexing never throws. If the embedding server is down, chunks are stored without
  vectors and retrieval falls back to FTS only.
- Embedding vectors are reused when the chunk text is unchanged, so re-indexing is cheap.
- Staleness: chunk rows remember `source_updated_date`; if the source was edited after
  indexing the hit is marked `stale=true` and the outstanding re-index will refresh it.
- Deleted sources (including sync tombstones) are phased out of the index by the diff pass.
- Memory: cosine search loads all embedded chunks per query. Fine at personal-corpus scale
  (10k chunks × 384 dims ≈ 15 MB); switch to a blockwise similarity query if that grows.

## Implemented UI (iteration 2)

- **Settings → Integrations → "Knowledge index" card** (`KnowledgeIndexSettingsSection.kt`):
  provider dropdown (None / Ollama / OpenAI-compatible), embedding model field
  (`SavableTextField`, saves on button), chunk/embedded counts (`KnowledgeIndexer.stats()`),
  Re-index (`reindexAll()`) and Clear (`clearAll()`) buttons. Wired through
  `IntegrationsViewModel` + `IntegrationsEvent.SelectKnowledgeEmbeddingProvider /
  UpdateKnowledgeEmbeddingModel / ReindexKnowledge / ClearKnowledgeIndex`.
  No raw prefs editing needed anymore — ignores the manual pref instructions above
  (kept for reference).
- **Semantic search** in the notes and diary search screens: search query (debounced
  400 ms) also runs `KnowledgeRetriever.retrieve(topK = 8)` and the hits render as a
  "Semantic matches" section above the keyword results (works in both list and
  staggered-grid note views). Hits navigate to `NoteDetailsScreen(noteId=…)` /
  `DiaryDetailScreen(entryId=…)`. Shared hit card: `core/ui/.../components/common/SemanticSearchItem.kt`.
- **Assistant previews**: `AiToolExecutor.extractResultObject` maps note-type hits of
  `retrieveKnowledge` onto the existing note preview cards (up to 5). Diary/bookmark
  hits stay text-only in the tool result since no preview object exists for them.

Module deps added: `ai.data` is now a dependency of `settings/presentation`,
`notes/presentation`, `diary/presentation` (all one-directional, no cycles).

## Remaining TODO (next iterations)

1. **Phase 3 — on-device embeddings**: all-MiniLM-L6-v2 int8 ONNX via `onnxruntime-android`
   in `core/embeddings` KMP module; implement `OnDeviceEmbeddingService` (expect/actual),
   tokenize → pooling → L2 normalize, LRU cache keyed by text hash.
2. Optional: cross-encoder reranking (top-24 → top-6) once embed quality is measurable.
3. Bookmarks: indexing currently uses title/description/url — fetching page content would
   make bookmark retrieval far more useful (respect privacy opt-out).
4. Optional: expose a semantic-search toggle for users who want FTS-only search screens;
   currently the semantic section simply stays empty when no embedding provider is set.

## Manual test checklist

- [ ] 7 → 8 migration on existing install (schema verify passes)
- [ ] Fresh install (onCreate) then add note → ask assistant "what notes do I have about X?"
- [ ] Ollama offline → RAG answers still work from FTS hits
- [ ] Ollama online with `nomic-embed-text` → semantic hit for paraphrased query
- [ ] Edit a note → hit becomes `stale=true`, then refreshes after change pass
- [ ] Delete a note → no ghost hits
- [ ] Gemini Nano selected → injection present in responses (verify via chat answers)
