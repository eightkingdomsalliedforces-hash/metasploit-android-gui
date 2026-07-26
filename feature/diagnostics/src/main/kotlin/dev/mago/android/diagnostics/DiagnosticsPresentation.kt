package dev.mago.android.diagnostics

import dev.mago.android.model.DiagnosticEntry
import java.util.Locale

data class DiagnosticsPresentationInput(
    val appVersionName: String,
    val appVersionCode: Long,
    val minimumApi: Int,
    val bridgeVersion: Int,
    val bridgeSha256: String,
    val androidRelease: String?,
    val apiLevel: Int,
    val primaryAbi: String?,
    val metasploitVersion: String?,
    val currentStage: String,
    val lastSuccessfulStage: String?,
    val failureKind: String?,
    val errorCode: String?,
    val diagnosticEntries: List<DiagnosticEntry>,
)

data class DiagnosticsUiModel(
    val about: DiagnosticsAboutInfo,
    val system: DiagnosticsSystemInfo,
    val installation: DiagnosticsInstallationInfo,
    val bridgeEntries: List<DiagnosticsEntryUiModel>,
    val copySummary: String,
)

data class DiagnosticsAboutInfo(
    val appVersionName: String,
    val appVersionCode: Long,
    val minimumApi: Int,
    val bridgeVersion: Int,
    val bridgeSha256: String,
)

data class DiagnosticsSystemInfo(
    val androidRelease: String?,
    val apiLevel: Int,
    val primaryAbi: String?,
    val metasploitVersion: String?,
)

data class DiagnosticsInstallationInfo(
    val currentStage: String,
    val lastSuccessfulStage: String?,
    val failureKind: String?,
    val errorCode: String?,
)

data class DiagnosticsEntryUiModel(
    val key: String,
    val label: String,
    val displayValue: String,
)

object DiagnosticsPresenter {
    private val allowedBridgeKeys = listOf(
        "bridge.frameworkRepository",
        "bridge.msfconsole",
        "bridge.databaseInitialized",
        "bridge.databaseConfig",
        "bridge.databaseReady",
        "bridge.rpcConfigured",
        "bridge.rpcProcessRunning",
        "bridge.rpcPortOpen",
        "bridge.rpcHost",
        "bridge.rpcPort",
        "bridge.metasploitVersion",
    )

    private val deniedKeyFragments = setOf(
        "password",
        "token",
        "credential",
        "secret",
        "path",
        "prefix",
        "serial",
        "deviceid",
        "androidid",
    )

    fun present(input: DiagnosticsPresentationInput): DiagnosticsUiModel {
        val entriesByKey = input.diagnosticEntries.associateBy(DiagnosticEntry::key)
        val bridgeEntries = allowedBridgeKeys.mapNotNull { key ->
            val entry = entriesByKey[key] ?: return@mapNotNull null
            when {
                isDeniedDiagnosticKey(key) || entry.sensitive -> DiagnosticsEntryUiModel(
                    key = key,
                    label = entry.label,
                    displayValue = HIDDEN_VALUE,
                )

                key == RPC_HOST_KEY -> DiagnosticsEntryUiModel(
                    key = key,
                    label = "RPC localhost",
                    displayValue = when (localhostStatus(entry.value)) {
                        LocalhostStatus.TRUE -> "是"
                        LocalhostStatus.FALSE -> "否"
                        LocalhostStatus.UNKNOWN -> UNKNOWN_DISPLAY_VALUE
                    },
                )

                else -> DiagnosticsEntryUiModel(
                    key = key,
                    label = entry.label,
                    displayValue = entry.value,
                )
            }
        }

        val about = DiagnosticsAboutInfo(
            appVersionName = input.appVersionName,
            appVersionCode = input.appVersionCode,
            minimumApi = input.minimumApi,
            bridgeVersion = input.bridgeVersion,
            bridgeSha256 = input.bridgeSha256,
        )
        val system = DiagnosticsSystemInfo(
            androidRelease = input.androidRelease,
            apiLevel = input.apiLevel,
            primaryAbi = input.primaryAbi,
            metasploitVersion = input.metasploitVersion,
        )
        val installation = DiagnosticsInstallationInfo(
            currentStage = input.currentStage,
            lastSuccessfulStage = input.lastSuccessfulStage,
            failureKind = input.failureKind,
            errorCode = input.errorCode,
        )

        return DiagnosticsUiModel(
            about = about,
            system = system,
            installation = installation,
            bridgeEntries = bridgeEntries,
            copySummary = buildSummary(input, entriesByKey),
        )
    }

    internal fun isDeniedDiagnosticKey(key: String): Boolean {
        val normalized = key
            .lowercase(Locale.ROOT)
            .replace("_", "")
            .replace("-", "")
            .replace(".", "")
        return deniedKeyFragments.any(normalized::contains)
    }

    private fun buildSummary(
        input: DiagnosticsPresentationInput,
        entriesByKey: Map<String, DiagnosticEntry>,
    ): String = buildString {
        appendLine("MAGO Diagnostics")
        appendLine("App: ${input.appVersionName} (${input.appVersionCode})")
        appendLine("Android: ${input.androidRelease.orUnknown()} / API ${input.apiLevel}")
        appendLine("ABI: ${input.primaryAbi.orUnknown()}")
        appendLine("Minimum API: ${input.minimumApi}")
        appendLine("Bridge: v${input.bridgeVersion}")
        appendLine("Bridge SHA-256: ${input.bridgeSha256}")
        appendLine("Metasploit: ${input.metasploitVersion.orUnknown()}")
        appendLine()
        appendLine("Installation stage: ${input.currentStage}")
        appendLine("Last successful stage: ${input.lastSuccessfulStage.orUnknown()}")
        appendLine("Failure kind: ${input.failureKind.orNone()}")
        appendLine("Error code: ${input.errorCode.orNone()}")
        appendLine()

        allowedBridgeKeys.forEach { key ->
            if (key == RPC_HOST_KEY) {
                val status = entriesByKey[key]
                    ?.takeUnless { it.sensitive || isDeniedDiagnosticKey(it.key) }
                    ?.value
                    .let(::localhostStatus)
                appendLine("RPC localhost: ${status.summaryValue}")
                return@forEach
            }

            val entry = entriesByKey[key] ?: return@forEach
            if (entry.sensitive || isDeniedDiagnosticKey(entry.key)) return@forEach
            appendLine("${key.removePrefix("bridge.")}: ${entry.value}")
        }

        appendLine()
        appendLine("Privacy:")
        appendLine("- Device brand/model omitted")
        appendLine("- Device identifiers omitted")
        appendLine("- Credentials/tokens omitted")
        appendLine("- Paths and raw errors omitted")
        append("- This report is copied manually and is never uploaded automatically")
    }

    private fun String?.orUnknown(): String = this?.takeIf(String::isNotBlank) ?: UNKNOWN_SUMMARY_VALUE

    private fun String?.orNone(): String = this?.takeIf(String::isNotBlank) ?: NONE_SUMMARY_VALUE

    private fun localhostStatus(value: String?): LocalhostStatus = when {
        value == null -> LocalhostStatus.UNKNOWN
        value == "127.0.0.1" || value == "::1" || value.equals("localhost", ignoreCase = true) -> {
            LocalhostStatus.TRUE
        }
        else -> LocalhostStatus.FALSE
    }

    private enum class LocalhostStatus(val summaryValue: String) {
        TRUE("true"),
        FALSE("false"),
        UNKNOWN("unknown"),
    }

    private const val RPC_HOST_KEY = "bridge.rpcHost"
    private const val HIDDEN_VALUE = "已隱藏"
    private const val UNKNOWN_DISPLAY_VALUE = "尚未取得"
    private const val UNKNOWN_SUMMARY_VALUE = "unknown"
    private const val NONE_SUMMARY_VALUE = "none"
}