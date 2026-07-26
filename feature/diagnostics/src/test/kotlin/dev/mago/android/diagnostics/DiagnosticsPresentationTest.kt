package dev.mago.android.diagnostics

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.DiagnosticEntry
import org.junit.Test

class DiagnosticsPresentationTest {
    @Test
    fun `summary includes stable support fields in fixed order`() {
        val model = DiagnosticsPresenter.present(
            input(
                diagnosticEntries = listOf(
                    entry("bridge.rpcPort", "rpcPort", "55552"),
                    entry("bridge.frameworkRepository", "frameworkRepository", "true"),
                    entry("bridge.rpcHost", "rpcHost", "127.0.0.1"),
                    entry("bridge.databaseReady", "databaseReady", "true"),
                ),
            ),
        )

        val summary = model.copySummary
        assertThat(summary).contains("App: 0.7.0 (7)")
        assertThat(summary).contains("Android: 16 / API 36")
        assertThat(summary).contains("ABI: arm64-v8a")
        assertThat(summary).contains("Minimum API: 31")
        assertThat(summary).contains("Bridge: v2")
        assertThat(summary).contains("Bridge SHA-256: 0123456789abcdef")
        assertThat(summary).contains("Metasploit: 6.4.99")
        assertThat(summary).contains("Installation stage: READY")
        assertThat(summary).contains("Last successful stage: VERIFYING")
        assertThat(summary).contains("Failure kind: RPC_ERROR")
        assertThat(summary).contains("Error code: RPC_UNAVAILABLE")
        assertThat(summary).contains("RPC localhost: true")
        assertThat(summary.indexOf("frameworkRepository: true"))
            .isLessThan(summary.indexOf("databaseReady: true"))
        assertThat(summary.indexOf("databaseReady: true"))
            .isLessThan(summary.indexOf("RPC localhost: true"))
        assertThat(summary).endsWith(
            "- This report is copied manually and is never uploaded automatically",
        )
    }

    @Test
    fun `summary and display fail closed for sensitive unknown and denied entries`() {
        val model = DiagnosticsPresenter.present(
            input(
                diagnosticEntries = listOf(
                    entry(
                        "bridge.frameworkRepository",
                        "frameworkRepository",
                        "SENSITIVE_ALLOWED_SECRET",
                        sensitive = true,
                    ),
                    entry("bridge.unknownFutureKey", "Pixel", "UNKNOWN_SECRET"),
                    entry("bridge.password", "password", "PASSWORD_SECRET"),
                    entry("bridge.token", "token", "TOKEN_SECRET"),
                    entry("bridge.path", "path", "/data/data/secret"),
                    entry("bridge.serial", "serial", "SERIAL_SECRET"),
                    entry("bridge.android_id", "androidId", "ANDROID_ID_SECRET"),
                    entry("bridge.rpcHost", "rpcHost", "198.51.100.8"),
                ),
            ),
        )

        val summary = model.copySummary
        assertThat(summary).doesNotContain("Pixel")
        assertThat(summary).doesNotContain("UNKNOWN_SECRET")
        assertThat(summary).doesNotContain("SENSITIVE_ALLOWED_SECRET")
        assertThat(summary).doesNotContain("PASSWORD_SECRET")
        assertThat(summary).doesNotContain("TOKEN_SECRET")
        assertThat(summary).doesNotContain("/data/data/secret")
        assertThat(summary).doesNotContain("SERIAL_SECRET")
        assertThat(summary).doesNotContain("ANDROID_ID_SECRET")
        assertThat(summary).doesNotContain("198.51.100.8")
        assertThat(summary).contains("RPC localhost: false")
        assertThat(model.bridgeEntries.map { it.key })
            .containsExactly(
                "bridge.frameworkRepository",
                "bridge.rpcHost",
            )
            .inOrder()
        assertThat(model.bridgeEntries.first().displayValue).isEqualTo("已隱藏")
        assertThat(model.bridgeEntries.last().displayValue).isEqualTo("否")
    }

    @Test
    fun `duplicate keys use final value and source order cannot change output order`() {
        val model = DiagnosticsPresenter.present(
            input(
                diagnosticEntries = listOf(
                    entry("bridge.rpcPort", "rpcPort", "4444"),
                    entry("bridge.rpcPortOpen", "rpcPortOpen", "true"),
                    entry("bridge.rpcPort", "rpcPort", "55552"),
                    entry("bridge.msfconsole", "msfconsole", "true"),
                ),
            ),
        )

        assertThat(model.bridgeEntries.map { it.key })
            .containsExactly(
                "bridge.msfconsole",
                "bridge.rpcPortOpen",
                "bridge.rpcPort",
            )
            .inOrder()
        assertThat(model.bridgeEntries.last().displayValue).isEqualTo("55552")
        assertThat(model.copySummary).contains("rpcPort: 55552")
        assertThat(model.copySummary).doesNotContain("rpcPort: 4444")
    }

    @Test
    fun `all supported localhost spellings derive true without exposing host`() {
        listOf("127.0.0.1", "::1", "LOCALHOST", "localhost").forEach { host ->
            val model = DiagnosticsPresenter.present(
                input(
                    diagnosticEntries = listOf(
                        entry("bridge.rpcHost", "rpcHost", host),
                    ),
                ),
            )

            assertThat(model.copySummary).contains("RPC localhost: true")
            assertThat(model.copySummary).doesNotContain("rpcHost: $host")
            assertThat(model.bridgeEntries.single().displayValue).isEqualTo("是")
        }
    }

    @Test
    fun `missing values use unknown and absent failure uses none`() {
        val model = DiagnosticsPresenter.present(
            input(
                androidRelease = null,
                primaryAbi = null,
                metasploitVersion = null,
                lastSuccessfulStage = null,
                failureKind = null,
                errorCode = null,
                diagnosticEntries = emptyList(),
            ),
        )

        assertThat(model.system.androidRelease).isNull()
        assertThat(model.system.primaryAbi).isNull()
        assertThat(model.system.metasploitVersion).isNull()
        assertThat(model.copySummary).contains("Android: unknown / API 36")
        assertThat(model.copySummary).contains("ABI: unknown")
        assertThat(model.copySummary).contains("Metasploit: unknown")
        assertThat(model.copySummary).contains("Last successful stage: unknown")
        assertThat(model.copySummary).contains("Failure kind: none")
        assertThat(model.copySummary).contains("Error code: none")
        assertThat(model.copySummary).contains("RPC localhost: unknown")
    }

    @Test
    fun `deny matcher normalizes separators and rejects every approved fragment`() {
        listOf(
            "bridge.rpc_password",
            "bridge.api-token",
            "bridge.credential",
            "bridge.clientSecret",
            "bridge.install.path",
            "bridge.prefix",
            "bridge.serial_number",
            "bridge.device-id",
            "bridge.android_id",
        ).forEach { key ->
            assertThat(DiagnosticsPresenter.isDeniedDiagnosticKey(key)).isTrue()
        }
        assertThat(DiagnosticsPresenter.isDeniedDiagnosticKey("bridge.databaseReady")).isFalse()
    }

    private fun input(
        androidRelease: String? = "16",
        primaryAbi: String? = "arm64-v8a",
        metasploitVersion: String? = "6.4.99",
        lastSuccessfulStage: String? = "VERIFYING",
        failureKind: String? = "RPC_ERROR",
        errorCode: String? = "RPC_UNAVAILABLE",
        diagnosticEntries: List<DiagnosticEntry> = emptyList(),
    ) = DiagnosticsPresentationInput(
        appVersionName = "0.7.0",
        appVersionCode = 7,
        minimumApi = 31,
        bridgeVersion = 2,
        bridgeSha256 = "0123456789abcdef",
        androidRelease = androidRelease,
        apiLevel = 36,
        primaryAbi = primaryAbi,
        metasploitVersion = metasploitVersion,
        currentStage = "READY",
        lastSuccessfulStage = lastSuccessfulStage,
        failureKind = failureKind,
        errorCode = errorCode,
        diagnosticEntries = diagnosticEntries,
    )

    private fun entry(
        key: String,
        label: String,
        value: String,
        sensitive: Boolean = false,
    ) = DiagnosticEntry(
        key = key,
        label = label,
        value = value,
        sensitive = sensitive,
    )
}