package dev.mago.android.database

import dev.mago.android.model.MetasploitModuleSummary

enum class ModuleCatalogWriteMode {
    REPLACE_TYPE,
    UPSERT_RESULTS,
}

object ModuleCatalogWritePolicy {
    fun mode(modules: List<MetasploitModuleSummary>): ModuleCatalogWriteMode {
        val containsSearchMetadata = modules.any { module ->
            module.displayName != null ||
                module.rank != null ||
                module.disclosureDate != null ||
                module.extraFields.isNotEmpty()
        }
        return if (containsSearchMetadata) {
            ModuleCatalogWriteMode.UPSERT_RESULTS
        } else {
            ModuleCatalogWriteMode.REPLACE_TYPE
        }
    }
}
