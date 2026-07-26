package dev.mago.android.modules

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.MetasploitModuleOption
import org.junit.Test

class ModuleRunValidatorTest {
    private val validator = ModuleRunValidator()

    @Test
    fun `required integer boolean and enum options are normalized`() {
        val result = validator.validate(
            options = listOf(
                option("RHOSTS", "address_range", required = true),
                option("RPORT", "port", required = true),
                option("SSL", "bool"),
                option("ACTION", "enum", enums = listOf("SCAN", "CHECK")),
            ),
            values = mapOf(
                "RHOSTS" to " 192.0.2.10 ",
                "RPORT" to "0443",
                "SSL" to "yes",
                "ACTION" to "check",
            ),
        )

        assertThat(result.valid).isTrue()
        assertThat(result.normalized).containsExactly(
            "RHOSTS", "192.0.2.10",
            "RPORT", "443",
            "SSL", "true",
            "ACTION", "CHECK",
        )
    }

    @Test
    fun `invalid values report field errors without emitting unsafe values`() {
        val result = validator.validate(
            options = listOf(
                option("RHOSTS", "address_range", required = true),
                option("RPORT", "port"),
                option("SSL", "bool"),
            ),
            values = mapOf(
                "RHOSTS" to "",
                "RPORT" to "70000",
                "SSL" to "maybe",
                "EXTRA" to "bad\u0000value",
            ),
        )

        assertThat(result.valid).isFalse()
        assertThat(result.errors.keys).containsExactly("RHOSTS", "RPORT", "SSL")
        assertThat(result.normalized).doesNotContainKey("EXTRA")
    }

    @Test
    fun `confirmation summary masks sensitive values`() {
        val summary = validator.redactedSummary(
            mapOf(
                "RHOSTS" to "192.0.2.10",
                "PASSWORD" to "secret",
                "API_TOKEN" to "token-value",
            ),
        )

        assertThat(summary["RHOSTS"]).isEqualTo("192.0.2.10")
        assertThat(summary["PASSWORD"]).isEqualTo("••••••••")
        assertThat(summary["API_TOKEN"]).isEqualTo("••••••••")
    }

    private fun option(
        name: String,
        type: String,
        required: Boolean = false,
        enums: List<String> = emptyList(),
    ) = MetasploitModuleOption(
        name = name,
        type = type,
        required = required,
        advanced = false,
        description = "",
        defaultValue = null,
        enums = enums,
    )
}
