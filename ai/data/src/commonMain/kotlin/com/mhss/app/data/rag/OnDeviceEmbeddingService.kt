package com.mhss.app.data.rag

import org.koin.core.annotation.Single

/**
 * Placeholder for the Phase-3 fully on-device embedding engine.
 *
 * Plan: run all-MiniLM-L6-v2 (int8 ONNX, ~30 MB) through `onnxruntime-android`
 * in an `androidMain` actual + hear the expect/actual pairing in a `core/embeddings`
 * module. Until then retrieval stays FTS-only, or one of the server-based
 * providers is used (Ollama / any OpenAI-compatible server), so this service is
 * registered but returns null to signal unavailability.
 */
@Single
class OnDeviceEmbeddingService : EmbeddingService {

    override suspend fun embed(texts: List<String>): List<List<Float>>? {
        // TODO(Phase 3): tokenize (sentencepiece) -> ONNX session (mean pooling,
        //  L2 normalize) -> return vectors. Keep an LRU cache keyed by text hash.
        return null
    }
}
