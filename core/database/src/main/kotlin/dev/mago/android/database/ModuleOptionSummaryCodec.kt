package dev.mago.android.database

import java.nio.charset.StandardCharsets
import java.util.Base64

object ModuleOptionSummaryCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(values: Map<String, String>): String = values
        .toSortedMap()
        .entries
        .joinToString("\n") { (name, value) -> "${encodePart(name)}:${encodePart(value)}" }

    fun decode(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        return encoded.lineSequence().associate { line ->
            val separator = line.indexOf(':')
            require(separator >= 0) { "Invalid module option summary record" }
            decodePart(line.substring(0, separator)) to decodePart(line.substring(separator + 1))
        }
    }

    private fun encodePart(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodePart(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)
}
