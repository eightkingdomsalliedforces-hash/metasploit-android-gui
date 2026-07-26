# MAGO Phase 6 Redacted Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export a user-initiated JSON or CSV report through Android Storage Access Framework containing the active Workspace’s bounded asset inventory and locally stored, redacted module execution history.

**Architecture:** `core:reporting` owns a pure structured snapshot, deterministic JSON/CSV builders, double-redaction policy, and the narrow SAF writer. `feature:reports` loads only whitelisted data from `MetasploitInventoryRepository` and `ModuleLocalStore`, builds one pending document after an explicit tap, and never polls. `MainActivity` owns the system document picker and reports the write result back to the ViewModel.

**Tech Stack:** Kotlin 2.4.10, Android API 31+, Coroutines/StateFlow, Jetpack Compose Material 3, Storage Access Framework, JUnit4/Truth, GitHub Actions.

## Global Constraints

- RPC remains fixed to `http://127.0.0.1:55552/api`.
- First slice supports JSON and CSV only; PDF, HTML, and ZIP remain separate later slices.
- Export is initiated by one explicit user action and never retries automatically.
- Each inventory collection is bounded to 100 records; module history is bounded to 100 records.
- Reports never include RPC passwords, RPC tokens, Credentials, Keystore data, Console input/output, Termux paths, model `extraFields`, asset comments/free-form info, module result summaries, or module errors.
- Module option values are re-masked at export time for names containing `PASSWORD`, `PASS`, `TOKEN`, `API_KEY`, `PRIVATE_KEY`, `SMBPASS`, `DB_PASSWORD`, `SECRET`, or `CREDENTIAL`.
- Storage uses SAF and requests no broad storage permission.
- Tests remain risk-directed: JSON/CSV escaping, secret exclusion, bounded snapshot loading, no partial export on source failure, and exact stream writing. Static Compose layout uses Build and Lint only.

---

## File Map

```text
core/reporting/build.gradle.kts
core/reporting/src/main/kotlin/dev/mago/android/reporting/ReportModels.kt
core/reporting/src/main/kotlin/dev/mago/android/reporting/DefaultReportDocumentBuilder.kt
core/reporting/src/main/kotlin/dev/mago/android/reporting/SafReportWriter.kt
core/reporting/src/test/kotlin/dev/mago/android/reporting/DefaultReportDocumentBuilderTest.kt
core/reporting/src/test/kotlin/dev/mago/android/reporting/ReportStreamWriterTest.kt

feature/reports/build.gradle.kts
feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt
feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt
feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt

app/src/main/kotlin/dev/mago/android/AppContainer.kt
app/src/main/kotlin/dev/mago/android/MainActivity.kt
app/src/main/kotlin/dev/mago/android/MagoApp.kt
app/src/main/kotlin/dev/mago/android/navigation/MagoDestination.kt
settings.gradle.kts
.github/workflows/android.yml
```

### Task 1: Define Safe Report Snapshot and Builders

**Interfaces:**

```kotlin
enum class ReportFormat(val extension: String, val mimeType: String)
data class ReportSnapshot(
    val generatedAtEpochMillis: Long,
    val workspace: MetasploitWorkspaceSummary,
    val hosts: List<MetasploitHostRecord>,
    val services: List<MetasploitServiceRecord>,
    val vulnerabilities: List<MetasploitVulnerabilityRecord>,
    val executions: List<ModuleExecutionRecord>,
)
data class ReportDocument(
    val id: String,
    val format: ReportFormat,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
interface ReportDocumentBuilder {
    fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument
}
```

- [ ] Write tests with secret values in `PASSWORD`, `TOKEN`, `resultSummary`, `error`, asset `info`, comments, and `extraFields`.
- [ ] Assert JSON and CSV contain no secret values and no excluded fields.
- [ ] Assert JSON escaping and RFC-4180 CSV quoting for quotes, commas, CR, and LF.
- [ ] Implement whitelist-only serialization and export-time option redaction.
- [ ] Use ISO-8601 UTC timestamps and deterministic record ordering.

### Task 2: Add Narrow SAF Writer

**Interfaces:**

```kotlin
class ReportStreamWriter {
    fun write(output: OutputStream, document: ReportDocument)
}
class SafReportWriter(
    private val contentResolver: ContentResolver,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun write(uri: Uri, document: ReportDocument): AppResult<Unit>
}
```

- [ ] Test that `ReportStreamWriter` writes exact bytes, flushes, and closes only through caller-owned `use`.
- [ ] Implement `SafReportWriter` with `openOutputStream(uri, "wt")`, `use`, IO dispatcher, and fail-closed `AppError` results.
- [ ] Do not add storage permissions or direct filesystem paths.

### Task 3: Build Explicit Snapshot State Machine

**Interfaces:**

```kotlin
data class ReportsUiState(
    val format: ReportFormat = ReportFormat.JSON,
    val loading: Boolean = false,
    val activeWorkspace: String? = null,
    val pendingDocument: ReportDocument? = null,
    val errorMessage: String? = null,
    val saveMessage: String? = null,
)
```

- [ ] Test that one export request calls current Workspace, Hosts, Services, Vulnerabilities, and execution history exactly once with limits of 100.
- [ ] Test that any source failure produces no document and performs no hidden retry.
- [ ] Test that consuming a document clears it so configuration changes do not relaunch the picker.
- [ ] Implement sequential fail-closed loading and builder invocation only after every required source succeeds.

### Task 4: Add Reports UI and SAF Picker Wiring

- [ ] Add a Reports destination and navigation icon.
- [ ] Render JSON/CSV format selection, exact included/excluded data notice, active Workspace, loading/error/save states, and one export button.
- [ ] In `MainActivity`, use two `CreateDocument` launchers with MIME types `application/json` and `text/csv`.
- [ ] Keep the pending `ReportDocument` in Compose state until the picker callback returns.
- [ ] Write through `SafReportWriter`; report success/failure to `ReportsViewModel`.
- [ ] A cancelled picker writes nothing and records a neutral cancellation message.

### Task 5: CI and Verification

- [ ] Add `:core:reporting:testDebugUnitTest` and `:feature:reports:testDebugUnitTest` to the existing risk-based CI command.
- [ ] Run Termux Bridge verification, Android assemble, Lint, and the full risk-based unit-test command in GitHub Actions.
- [ ] Fix only evidence-backed failures.
- [ ] Download the successful APK Artifact and verify the Artifact digest, ZIP integrity, APK size, and APK SHA-256 before merge.

## Self-Review

- Scope covers only the approved first reporting slice: JSON, CSV, SAF, active Workspace assets, and redacted execution history.
- Data flow is whitelist-based; excluded fields are never passed to generic object serialization.
- No Credentials API, Console API, background polling, broad storage permission, direct path, or automatic retry is introduced.
- Tests focus on the failure boundaries that could expose secrets or produce invalid files; no screenshot-test suite is added.
