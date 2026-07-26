package dev.mago.android.database

import dev.mago.android.database.entity.ModuleIndexEntity
import dev.mago.android.database.entity.ModuleSearchFtsEntity
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

class ModuleCatalogMapper {
    fun fromSummary(
        value: MetasploitModuleSummary,
        refreshedAtEpochMillis: Long,
    ): ModuleIndexEntity = ModuleIndexEntity(
        type = value.type.rpcName,
        name = value.name,
        displayName = value.name,
        description = "",
        rank = null,
        platformsText = "",
        architecturesText = "",
        authorsText = "",
        refreshedAtEpochMillis = refreshedAtEpochMillis,
    )

    fun fromInfo(
        value: MetasploitModuleInfo,
        refreshedAtEpochMillis: Long,
    ): ModuleIndexEntity = ModuleIndexEntity(
        type = value.type.rpcName,
        name = value.name,
        displayName = value.displayName,
        description = value.description,
        rank = value.rank,
        platformsText = encodeList(value.platforms),
        architecturesText = encodeList(value.architectures),
        authorsText = encodeList(value.authors),
        refreshedAtEpochMillis = refreshedAtEpochMillis,
    )

    fun toSearch(value: ModuleIndexEntity): ModuleSearchFtsEntity = ModuleSearchFtsEntity(
        type = value.type,
        name = value.name,
        displayName = value.displayName,
        description = value.description,
        platformsText = value.platformsText,
        architecturesText = value.architecturesText,
        authorsText = value.authorsText,
    )

    fun toSummary(value: ModuleIndexEntity): MetasploitModuleSummary? {
        val type = typeOrNull(value.type) ?: return null
        return MetasploitModuleSummary(type = type, name = value.name)
    }

    fun toInfo(value: ModuleIndexEntity): MetasploitModuleInfo? {
        val type = typeOrNull(value.type) ?: return null
        return MetasploitModuleInfo(
            type = type,
            name = value.name,
            displayName = value.displayName,
            description = value.description,
            rank = value.rank,
            platforms = decodeList(value.platformsText),
            architectures = decodeList(value.architecturesText),
            authors = decodeList(value.authorsText),
            privileged = false,
            hasCheck = false,
            stance = null,
            references = emptyList(),
            options = emptyList(),
            extraFields = emptyMap(),
        )
    }

    private fun typeOrNull(value: String): MetasploitModuleType? =
        MetasploitModuleType.entries.firstOrNull { it.rpcName == value }

    private fun encodeList(values: List<String>): String = values
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(LIST_SEPARATOR)

    private fun decodeList(value: String): List<String> = if (value.isBlank()) {
        emptyList()
    } else {
        value.split(LIST_SEPARATOR).filter(String::isNotBlank)
    }

    private companion object {
        const val LIST_SEPARATOR = "\u001F"
    }
}
