package dev.mago.android.security

import java.util.Base64

data class RpcSecretRecord(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

class RpcSecretRecordCodec {
    fun encode(iv: ByteArray, ciphertext: ByteArray): String {
        require(iv.isNotEmpty()) { "IV must not be empty" }
        require(ciphertext.isNotEmpty()) { "Ciphertext must not be empty" }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf(VERSION, encoder.encodeToString(iv), encoder.encodeToString(ciphertext)).joinToString(".")
    }

    fun decode(value: String): RpcSecretRecord? {
        val parts = value.split('.')
        if (parts.size != 3 || parts[0] != VERSION) return null
        return try {
            val decoder = Base64.getUrlDecoder()
            val iv = decoder.decode(parts[1])
            val ciphertext = decoder.decode(parts[2])
            if (iv.isEmpty() || ciphertext.isEmpty()) null else RpcSecretRecord(iv, ciphertext)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val VERSION = "v1"
    }
}
