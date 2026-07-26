package dev.mago.android.database

import java.util.Base64

class RedactedParameterCodec {
    fun encode(values: Map<String, String>): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val body = values.toSortedMap().entries.joinToString("&") { (name, value) ->
            val encodedName = encoder.encodeToString(name.toByteArray(Charsets.UTF_8))
            val encodedValue = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
            "$encodedName.$encodedValue"
        }
        return "$VERSION:$body"
    }

    fun decode(value: String): Map<String, String>? {
        if (!value.startsWith("$VERSION:")) return null
        val body = value.substringAfter(':')
        if (body.isEmpty()) return emptyMap()
        val decoder = Base64.getUrlDecoder()
        return try {
            buildMap {
                body.split('&').forEach { pair ->
                    val parts = pair.split('.', limit = 2)
                    if (parts.size != 2) return null
                    val name = decoder.decode(parts[0]).toString(Charsets.UTF_8)
                    val decodedValue = decoder.decode(parts[1]).toString(Charsets.UTF_8)
                    put(name, decodedValue)
                }
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val VERSION = "v1"
    }
}
