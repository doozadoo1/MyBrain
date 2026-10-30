package com.mhss.app.data.rag

/**
 * Splits long text into overlapping chunks suited for embedding and retrieval.
 *
 * Chunks prefer paragraph boundaries, then sentence boundaries, and fall back
 * to raw slicing. The title is prepended to each chunk so it is always present
 * in the embedded/searched text.
 */
internal object TextChunker {

    const val MAX_CHUNK_LENGTH = 1000
    const val OVERLAP_LENGTH = 120
    private const val MIN_CHUNK_LENGTH = 40

    data class Chunk(val index: Int, val text: String)

    fun chunk(title: String, content: String, maxChars: Int = MAX_CHUNK_LENGTH): List<Chunk> {
        val titleLine = title.trim()
        val body = content.trim()
        if (body.isBlank() && titleLine.isBlank()) return emptyList()

        val blocks = splitIntoBlocks(body.ifBlank { titleLine })
        val pieces = mutableListOf<String>()
        var current = StringBuilder()
        for (block in blocks) {
            if (block.length > maxChars) {
                if (current.isNotBlank()) {
                    pieces.add(current.toString().trim())
                    current = StringBuilder()
                }
                pieces.addAll(splitLongBlock(block, maxChars))
            } else {
                if (current.length + block.length + 2 > maxChars && current.isNotBlank()) {
                    pieces.add(current.toString().trim())
                    current = StringBuilder()
                }
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(block)
            }
        }
        if (current.isNotBlank()) pieces.add(current.toString().trim())

        val chunks = pieces
            .filter { it.isNotBlank() }
            .mapIndexed { index, piece ->
                val text = if (titleLine.isNotBlank()) "$titleLine\n$piece" else piece
                Chunk(index = index, text = text)
            }
        // Merge trailing tiny chunk into the previous one when it fits
        if (chunks.size > 1 && chunks.last().text.length < MIN_CHUNK_LENGTH) {
            val last = chunks.last()
            val prev = chunks[chunks.size - 2]
            if (prev.text.length + last.text.length <= maxChars * 2) {
                val base = chunks.dropLast(2) + Chunk(
                    index = prev.index,
                    text = prev.text + "\n" + last.text
                )
                return base.mapIndexed { position, chunk -> chunk.copy(index = position) }
            }
        }
        return chunks
    }

    private fun splitIntoBlocks(text: String): List<String> {
        val paragraphs = text.split(Regex("\\n{2,}"))
        val result = mutableListOf<String>()
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isBlank()) continue
            // Split very long paragraphs on markdown headings first
            val withHeadings = trimmed.split(Regex("\\n(?=#{1,6}\\s)"))
            for (part in withHeadings) {
                val p = part.trim()
                if (p.isNotBlank()) result.add(p)
            }
        }
        return result.ifEmpty { listOf(text) }
    }

    private fun splitLongBlock(block: String, maxChars: Int): List<String> {
        val sentences = splitSentences(block)
        val pieces = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > maxChars) {
                if (current.isNotBlank()) {
                    pieces.add(current.toString().trim())
                    current = StringBuilder()
                }
                pieces.addAll(sliceWithOverlap(sentence, maxChars))
                continue
            }
            if (current.length + sentence.length + 1 > maxChars && current.isNotBlank()) {
                pieces.add(current.toString().trim())
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(" ")
            current.append(sentence)
        }
        if (current.isNotBlank()) pieces.add(current.toString().trim())
        return pieces
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?;\\u06D4\\u3002])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text) }

    private fun sliceWithOverlap(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + maxChars, text.length)
            var cut = end
            if (end < text.length) {
                // Prefer cutting on a space to avoid slicing through words
                val lastSpace = text.lastIndexOf(' ', end - 1)
                if (lastSpace > start + maxChars / 2) cut = lastSpace
            }
            result.add(text.substring(start, cut).trim())
            if (cut >= text.length) break
            start = maxOf(cut - OVERLAP_LENGTH, start + 1)
        }
        return result.filter { it.isNotBlank() }
    }
}
