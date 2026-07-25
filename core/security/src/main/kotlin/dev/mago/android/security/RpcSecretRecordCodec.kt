package dev.mago.android.security

import java.util.Base64

data class RpcSecretRecord(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

object RpcSecretRecordCodec {
    private const val VERSION = "v1"

    fun encode(record: RpcSecretRecord): String = buildString {
        append(VERSION)
        append('.')
        append(Base64.getEncoder().encodeToString(record.iv))
        append('.')
        append(Base64.getEncoder().encodeToString(record.ciphertext))
    }

    fun decode(value: String): RpcSecretRecord? {
        val parts = value.split('.')
        if (parts.size != 3 || parts[0] != VERSION) return null
        return try {
            val iv = Base64.getDecoder().decode(parts[1])
            val ciphertext = Base64.getDecoder().decode(parts[2])
            if (iv.size != 12 || ciphertext.isEmpty()) null else RpcSecretRecord(iv, ciphertext)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
