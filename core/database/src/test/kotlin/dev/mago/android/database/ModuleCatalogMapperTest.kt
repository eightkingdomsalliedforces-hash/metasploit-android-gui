package dev.mago.android.database

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleType
import org.junit.Test

class ModuleCatalogMapperTest {
    private val mapper = ModuleCatalogMapper()

    @Test
    fun `detail cache preserves searchable metadata without RPC-only fields`() {
        val source = MetasploitModuleInfo(
            type = MetasploitModuleType.EXPLOIT,
            name = "windows/example",
            displayName = "Example Module",
            description = "Authorized lab example",
            rank = "normal",
            platforms = listOf("Windows", "Linux"),
            architectures = listOf("x64"),
            authors = listOf("Researcher One"),
            privileged = true,
            hasCheck = true,
            stance = "aggressive",
            references = emptyList(),
            options = emptyList(),
            extraFields = emptyMap(),
        )

        val entity = mapper.fromInfo(source, refreshedAtEpochMillis = 1234)
        val restored = mapper.toInfo(entity)

        assertThat(restored?.type).isEqualTo(MetasploitModuleType.EXPLOIT)
        assertThat(restored?.name).isEqualTo("windows/example")
        assertThat(restored?.displayName).isEqualTo("Example Module")
        assertThat(restored?.platforms).containsExactly("Windows", "Linux").inOrder()
        assertThat(restored?.architectures).containsExactly("x64")
        assertThat(restored?.authors).containsExactly("Researcher One")
        assertThat(restored?.options).isEmpty()
        assertThat(restored?.references).isEmpty()
    }

    @Test
    fun `unknown future module type is ignored instead of crashing`() {
        val entity = mapper.fromInfo(
            value = MetasploitModuleInfo(
                type = MetasploitModuleType.AUXILIARY,
                name = "scanner/example",
                displayName = "Example",
                description = "",
                rank = null,
                platforms = emptyList(),
                architectures = emptyList(),
                authors = emptyList(),
                privileged = false,
                hasCheck = false,
                stance = null,
                references = emptyList(),
                options = emptyList(),
                extraFields = emptyMap(),
            ),
            refreshedAtEpochMillis = 1,
        ).copy(type = "future-module-type")

        assertThat(mapper.toSummary(entity)).isNull()
        assertThat(mapper.toInfo(entity)).isNull()
    }
}
