package dev.mago.android.database

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import org.junit.Test

class ModuleCatalogWritePolicyTest {
    @Test
    fun `plain type listing replaces the cached type`() {
        val modules = listOf(
            MetasploitModuleSummary(MetasploitModuleType.EXPLOIT, "windows/example"),
        )

        assertThat(ModuleCatalogWritePolicy.mode(modules))
            .isEqualTo(ModuleCatalogWriteMode.REPLACE_TYPE)
    }

    @Test
    fun `enriched search results only upsert matching rows`() {
        val modules = listOf(
            MetasploitModuleSummary(
                type = MetasploitModuleType.EXPLOIT,
                name = "windows/smb/example",
                displayName = "Example SMB Module",
                rank = "excellent",
            ),
        )

        assertThat(ModuleCatalogWritePolicy.mode(modules))
            .isEqualTo(ModuleCatalogWriteMode.UPSERT_RESULTS)
    }
}
