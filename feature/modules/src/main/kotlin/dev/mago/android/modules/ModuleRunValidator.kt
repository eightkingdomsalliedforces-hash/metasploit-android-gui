package dev.mago.android.modules

import dev.mago.android.model.MetasploitModuleOption

data class ModuleRunValidation(
    val errors: Map<String, String>,
    val normalized: Map<String, String>,
) {
    val valid: Boolean
        get() = errors.isEmpty()
}

class ModuleRunValidator {
    fun validate(
        options: List<MetasploitModuleOption>,
        values: Map<String, String>,
    ): ModuleRunValidation {
        val errors = linkedMapOf<String, String>()
        val normalized = linkedMapOf<String, String>()

        options.forEach { option ->
            val raw = values[option.name].orEmpty()
            val value = raw.trim()
            if (value.isEmpty()) {
                if (option.required) errors[option.name] = "此欄位為必填"
                return@forEach
            }
            if (value.utf8Size() > MAX_VALUE_BYTES) {
                errors[option.name] = "內容不可超過 8 KiB"
                return@forEach
            }
            if (value.any(Char::isISOControl)) {
                errors[option.name] = "內容不可包含控制字元"
                return@forEach
            }

            val normalizedValue = when {
                option.enums.isNotEmpty() -> {
                    val canonical = option.enums.firstOrNull { it.equals(value, ignoreCase = true) }
                    if (canonical == null) {
                        errors[option.name] = "請選擇有效值"
                        null
                    } else {
                        canonical
                    }
                }
                option.type.lowercase() in INTEGER_TYPES -> {
                    val number = value.toLongOrNull()
                    if (number == null) {
                        errors[option.name] = "請輸入整數"
                        null
                    } else if (option.type.equals("port", ignoreCase = true) && number !in 1..65535) {
                        errors[option.name] = "Port 必須介於 1 到 65535"
                        null
                    } else {
                        number.toString()
                    }
                }
                option.type.equals("bool", ignoreCase = true) ||
                    option.type.equals("boolean", ignoreCase = true) -> normalizeBoolean(value).also {
                        if (it == null) errors[option.name] = "請輸入 true 或 false"
                    }
                else -> value
            }
            if (normalizedValue != null) normalized[option.name] = normalizedValue
        }

        values.forEach { (name, raw) ->
            if (options.none { it.name == name }) {
                val value = raw.trim()
                if (
                    value.isNotEmpty() &&
                    value.utf8Size() <= MAX_VALUE_BYTES &&
                    value.none(Char::isISOControl)
                ) {
                    normalized[name] = value
                }
            }
        }

        return ModuleRunValidation(errors = errors, normalized = normalized)
    }

    fun redactedSummary(values: Map<String, String>): Map<String, String> =
        values
            .filterValues { it.isNotBlank() }
            .toSortedMap()
            .mapValues { (name, value) -> if (isSensitive(name)) MASK else value }

    fun isSensitive(name: String): Boolean {
        val upper = name.uppercase()
        val tokens = upper.split(NON_IDENTIFIER).filter(String::isNotEmpty)
        return tokens.any { it in SENSITIVE_TOKENS } ||
            upper.endsWith("PASS") ||
            upper.endsWith("PASSWORD")
    }

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    private fun normalizeBoolean(value: String): String? = when (value.lowercase()) {
        "true", "1", "yes", "on" -> "true"
        "false", "0", "no", "off" -> "false"
        else -> null
    }

    private companion object {
        const val MAX_VALUE_BYTES = 8 * 1024
        const val MASK = "••••••••"
        val INTEGER_TYPES = setOf("int", "integer", "port")
        val SENSITIVE_TOKENS = setOf("PASS", "PASSWORD", "TOKEN", "KEY", "SECRET", "CREDENTIAL")
        val NON_IDENTIFIER = Regex("[^A-Z0-9]+")
    }
}
