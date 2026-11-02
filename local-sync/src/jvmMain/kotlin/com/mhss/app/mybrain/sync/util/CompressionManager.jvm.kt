package com.mhss.app.mybrain.sync.util

import com.github.luben.zstd.Zstd
import org.koin.core.annotation.Single

private const val COMPRESSION_LEVEL = 1
private const val COMPRESSION_THRESHOLD = 2048
private val MAGIC = Zstd.magicNumber()

@Single
actual class CompressionManager {

    actual fun compress(input: ByteArray): ByteArray =
        if (input.size > COMPRESSION_THRESHOLD) Zstd.compress(input, COMPRESSION_LEVEL) else input

    actual fun decompress(input: ByteArray): ByteArray {
        return try {
            if (!isCompressed(input)) return input
            val size = Zstd.getFrameContentSize(input).toInt()
            ByteArray(size).also { Zstd.decompress(it, input) }
        } catch (_: Throwable) {
            input
        }
    }

    private fun isCompressed(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        return MAGIC == b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
