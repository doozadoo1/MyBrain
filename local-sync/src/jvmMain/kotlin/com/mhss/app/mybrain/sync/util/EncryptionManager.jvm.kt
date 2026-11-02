package com.mhss.app.mybrain.sync.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual class EncryptionManager actual constructor() {

    private val secureRandom = SecureRandom()
    private val keySpecs = concurrentMutableMap<String, SecretKeySpec>()

    actual fun encrypt(input: ByteArray, secretKey: String): ByteArray {
        val iv = ByteArray(IV_LENGTH).apply { secureRandom.nextBytes(this) }
        val cipher = buildCipher(Cipher.ENCRYPT_MODE, iv, secretKey)
        val encrypted = cipher.doFinal(input)
        return iv + encrypted
    }

    actual fun decrypt(input: ByteArray, secretKey: String): ByteArray {
        val iv = input.copyOfRange(0, IV_LENGTH)
        val encrypted = input.copyOfRange(IV_LENGTH, input.size)
        val cipher = buildCipher(Cipher.DECRYPT_MODE, iv, secretKey)
        return cipher.doFinal(encrypted)
    }

    actual fun removeKey(secretKey: String) {
        keySpecs.remove(secretKey)
    }

    private fun buildCipher(mode: Int, iv: ByteArray, secretKey: String): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, keySpec(secretKey), GCMParameterSpec(TAG_LENGTH, iv))
        }
    }

    private fun keySpec(secretKey: String): SecretKeySpec {
        return keySpecs.getOrPut(secretKey) {
            val keyBytes = Base64.getDecoder().decode(secretKey.replace(' ', '+').trim())
            require(keyBytes.size == KEY_LENGTH) { "Invalid AES key length, expected 32 bytes" }
            SecretKeySpec(keyBytes, "AES")
        }
    }
}

private const val KEY_LENGTH = 32
private const val IV_LENGTH = 12
private const val TAG_LENGTH = 128
private const val TRANSFORMATION = "AES/GCM/NoPadding"
