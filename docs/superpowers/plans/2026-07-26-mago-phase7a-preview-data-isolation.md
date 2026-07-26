# MAGO Phase 7A Preview Data Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one in-memory report preview snapshot that is loaded atomically, reused for export, and converted into an explicitly sanitized `ReportSnapshot` before JSON／CSV／HTML／ZIP generation.

**Architecture:** `feature:reports` owns `ReportPreviewSnapshot`, preview load state, and the conversion to the existing `core:reporting` export model. `ReportsViewModel` loads Workspace、Hosts、Services、Vulnerabilities and local execution history once, replaces the snapshot only after all five sources succeed, and exports only from the current snapshot without new repository calls. `app` only wires the report destination entry callback and SAF event flow.

**Tech Stack:** Kotlin, Android ViewModel, Kotlin Coroutines／StateFlow, Jetpack Compose, JUnit 4, Truth, kotlinx-coroutines-test, Gradle 9.5.0, JDK 17, Android SDK 36.

## Global Constraints

- Base every implementation branch on the latest `main`; never base work on PR #12 or `feature/phase4-jobs-sessions`.
- Android minimum remains API 31; compile SDK and Build Tools remain 36／36.0.0.
- Keep RPC access bounded to the existing repository methods and `127.0.0.1`; add no Credentials、Loot、Session automation or new network endpoint.
- Preview data remains only in `ReportsViewModel` memory; do not write it to Room、SavedStateHandle、Logcat、exceptions、analytics or CI artifacts.
- Each source remains limited to 100 records at offset 0; no pagination、background polling or automatic retry in Phase 7A.
- JSON／CSV／HTML／ZIP output must continue excluding `info`、`comments`、`extraFields`、RPC secrets、Console data、raw results and raw errors.
- Existing Storage Access Framework launchers and document writing behavior remain unchanged.
- Use TDD for every state transition and conversion rule; run focused tests before the full module suite.

---

## File Structure

- Create `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewModels.kt`
  - Owns `ReportPreviewSnapshot` and `toSafeReportSnapshot()`.
  - Contains no Compose、repository or Android framework dependency.
- Create `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewModelsTest.kt`
  - Proves unsafe preview-only fields are removed before export.
- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
  - Separates preview loading、refreshing and exporting state.
  - Loads all sources atomically and exports from memory only.
- Modify `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`
  - Replaces direct-export tests with load／refresh／export state-machine tests.
- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`
  - Adds the one-time destination-entry callback and minimal Phase 7A loading／snapshot messaging.
  - Does not implement the full preview list; that belongs to Phase 7B.
- Modify `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
  - Passes `onReportEnsurePreviewLoaded` into `ReportsScreen`.
- Modify `app/src/main/kotlin/dev/mago/android/MainActivity.kt`
  - Wires `reportsViewModel::ensurePreviewLoaded` into `MagoApp`; SAF launch behavior remains unchanged.

---

### Task 1: Add the preview snapshot and explicit safe conversion

**Files:**
- Create: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewModels.kt`
- Create: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewModelsTest.kt`

**Interfaces:**
- Consumes: `MetasploitWorkspaceSummary`, `MetasploitHostRecord`, `MetasploitServiceRecord`, `MetasploitVulnerabilityRecord`, `ModuleExecutionRecord`, and existing `ReportSnapshot`.
- Produces:
  - `data class ReportPreviewSnapshot(...)`
  - `fun ReportPreviewSnapshot.toSafeReportSnapshot(): ReportSnapshot`

- [ ] **Step 1: Write the failing conversion test**

Create `ReportPreviewModelsTest.kt` with a preview snapshot that contains sentinel values in every preview-only field:

```kotlin
package dev.mago.android.reports

import com.google.common.truth.Truth.assertThat
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import dev.mago.android.model.rpc.RpcValue
import org.junit.Test

class ReportPreviewModelsTest {
    @Test
    fun `safe snapshot preserves export fields and removes preview-only fields`() {
        val execution = ModuleExecutionRecord(
            correlationId = "correlation-1",
            action = MetasploitModuleRunAction.CHECK,
            type = MetasploitModuleType.AUXILIARY,
            name = "scanner/test",
            status = MetasploitModuleRunStatus.READY,
            jobId = null,
            uuid = null,
            redactedOptions = mapOf("PASSWORD" to "[REDACTED]"),
            resultSummary = null,
            error = null,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_100,
        )
        val preview = ReportPreviewSnapshot(
            generatedAtEpochMillis = 1_700_000_000_000,
            workspace = MetasploitWorkspaceSummary(
                id = 7,
                name = "lab",
                createdAtEpochSeconds = 11,
                updatedAtEpochSeconds = 12,
                extraFields = mapOf("workspace_secret" to RpcValue.StringValue("WORKSPACE_SECRET")),
            ),
            hosts = listOf(
                MetasploitHostRecord(
                    address = "192.0.2.10",
                    mac = "00:11:22:33:44:55",
                    name = "target",
                    state = "alive",
                    operatingSystem = "Linux",
                    operatingSystemFlavor = "Ubuntu",
                    servicePack = "PREVIEW_SERVICE_PACK",
                    language = "PREVIEW_LANGUAGE",
                    purpose = "server",
                    info = "HOST_INFO_SECRET",
                    comments = "HOST_COMMENT_SECRET",
                    createdAtEpochSeconds = 21,
                    updatedAtEpochSeconds = 22,
                    extraFields = mapOf("host_secret" to RpcValue.StringValue("HOST_EXTRA_SECRET")),
                ),
            ),
            services = listOf(
                MetasploitServiceRecord(
                    host = "192.0.2.10",
                    port = 443,
                    protocol = "tcp",
                    state = "open",
                    name = "https",
                    info = "SERVICE_INFO_SECRET",
                    createdAtEpochSeconds = 31,
                    updatedAtEpochSeconds = 32,
                    extraFields = mapOf("service_secret" to RpcValue.StringValue("SERVICE_EXTRA_SECRET")),
                ),
            ),
            vulnerabilities = listOf(
                MetasploitVulnerabilityRecord(
                    host = "192.0.2.10",
                    port = 443,
                    protocol = "tcp",
                    name = "CVE-TEST",
                    references = listOf("CVE-TEST"),
                    resource = "VULN_RESOURCE_SECRET",
                    reportedAtEpochSeconds = 41,
                    extraFields = mapOf("vuln_secret" to RpcValue.StringValue("VULN_EXTRA_SECRET")),
                ),
            ),
            executions = listOf(execution),
        )

        val safe = preview.toSafeReportSnapshot()

        assertThat(safe.generatedAtEpochMillis).isEqualTo(preview.generatedAtEpochMillis)
        assertThat(safe.workspace.id).isEqualTo(7)
        assertThat(safe.workspace.name).isEqualTo("lab")
        assertThat(safe.workspace.createdAtEpochSeconds).isNull()
        assertThat(safe.workspace.updatedAtEpochSeconds).isNull()
        assertThat(safe.workspace.extraFields).isEmpty()

        val host = safe.hosts.single()
        assertThat(host.address).isEqualTo("192.0.2.10")
        assertThat(host.name).isEqualTo("target")
        assertThat(host.state).isEqualTo("alive")
        assertThat(host.operatingSystem).isEqualTo("Linux")
        assertThat(host.operatingSystemFlavor).isEqualTo("Ubuntu")
        assertThat(host.purpose).isEqualTo("server")
        assertThat(host.mac).isNull()
        assertThat(host.servicePack).isNull()
        assertThat(host.language).isNull()
        assertThat(host.info).isNull()
        assertThat(host.comments).isNull()
        assertThat(host.createdAtEpochSeconds).isNull()
        assertThat(host.updatedAtEpochSeconds).isNull()
        assertThat(host.extraFields).isEmpty()

        val service = safe.services.single()
        assertThat(service.host).isEqualTo("192.0.2.10")
        assertThat(service.port).isEqualTo(443)
        assertThat(service.protocol).isEqualTo("tcp")
        assertThat(service.state).isEqualTo("open")
        assertThat(service.name).isEqualTo("https")
        assertThat(service.info).isNull()
        assertThat(service.createdAtEpochSeconds).isNull()
        assertThat(service.updatedAtEpochSeconds).isNull()
        assertThat(service.extraFields).isEmpty()

        val vulnerability = safe.vulnerabilities.single()
        assertThat(vulnerability.host).isEqualTo("192.0.2.10")
        assertThat(vulnerability.port).isEqualTo(443)
        assertThat(vulnerability.protocol).isEqualTo("tcp")
        assertThat(vulnerability.name).isEqualTo("CVE-TEST")
        assertThat(vulnerability.references).containsExactly("CVE-TEST")
        assertThat(vulnerability.resource).isNull()
        assertThat(vulnerability.reportedAtEpochSeconds).isNull()
        assertThat(vulnerability.extraFields).isEmpty()
        assertThat(safe.executions).containsExactly(execution)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the missing symbols fail**

Run:

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportPreviewModelsTest'
```

Expected: FAIL because `ReportPreviewSnapshot` and `toSafeReportSnapshot()` do not exist.

- [ ] **Step 3: Add the preview model and minimal explicit sanitizer**

Create `ReportPreviewModels.kt`:

```kotlin
package dev.mago.android.reports

import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import dev.mago.android.reporting.ReportSnapshot

data class ReportPreviewSnapshot(
    val generatedAtEpochMillis: Long,
    val workspace: MetasploitWorkspaceSummary,
    val hosts: List<MetasploitHostRecord>,
    val services: List<MetasploitServiceRecord>,
    val vulnerabilities: List<MetasploitVulnerabilityRecord>,
    val executions: List<ModuleExecutionRecord>,
)

fun ReportPreviewSnapshot.toSafeReportSnapshot(): ReportSnapshot = ReportSnapshot(
    generatedAtEpochMillis = generatedAtEpochMillis,
    workspace = workspace.copy(
        createdAtEpochSeconds = null,
        updatedAtEpochSeconds = null,
        extraFields = emptyMap(),
    ),
    hosts = hosts.map { host ->
        host.copy(
            mac = null,
            servicePack = null,
            language = null,
            info = null,
            comments = null,
            createdAtEpochSeconds = null,
            updatedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    },
    services = services.map { service ->
        service.copy(
            info = null,
            createdAtEpochSeconds = null,
            updatedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    },
    vulnerabilities = vulnerabilities.map { vulnerability ->
        vulnerability.copy(
            resource = null,
            reportedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    },
    executions = executions,
)
```

Do not modify `core:reporting` in this task. The sanitizer lives beside the preview model so `core:reporting` never needs to depend on `feature:reports`.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same Gradle command from Step 2.

Expected: PASS, one test executed.

- [ ] **Step 5: Run the existing report document tests to ensure output compatibility**

Run:

```bash
gradle --no-daemon \
  :core:reporting:testDebugUnitTest \
  :feature:reports:testDebugUnitTest
```

Expected: PASS with no existing report-format regression.

- [ ] **Step 6: Commit Task 1**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewModels.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewModelsTest.kt
git commit -m "feat: isolate report preview data from exports"
```

---

### Task 2: Refactor the ViewModel into an atomic preview loader

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
- Modify: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReportPreviewSnapshot` from Task 1 and existing repository／store methods.
- Produces:
  - `fun ensurePreviewLoaded()`
  - `fun refreshPreview()`
  - `ReportsUiState.previewSnapshot: ReportPreviewSnapshot?`
  - independent `initialLoading`, `refreshing`, and `exporting` flags.

- [ ] **Step 1: Replace the first ViewModel test with initial-load behavior**

Change the existing direct-export test to:

```kotlin
@Test
fun `initial preview load reads each bounded source once and stores one snapshot`() = runTest {
    val inventory = FakeInventoryRepository()
    val store = FakeModuleLocalStore()
    val builder = CapturingBuilder()
    val viewModel = ReportsViewModel(inventory, store, builder) { 1_700_000_000_000 }

    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()

    assertThat(inventory.currentCalls).isEqualTo(1)
    assertThat(inventory.hostCalls).isEqualTo(1)
    assertThat(inventory.serviceCalls).isEqualTo(1)
    assertThat(inventory.vulnerabilityCalls).isEqualTo(1)
    assertThat(inventory.lastLimit).isEqualTo(ReportsViewModel.RECORD_LIMIT)
    assertThat(inventory.lastOffset).isEqualTo(0)
    assertThat(store.historyCalls).isEqualTo(1)
    assertThat(store.lastHistoryLimit).isEqualTo(ReportsViewModel.RECORD_LIMIT)
    assertThat(builder.calls).isEqualTo(0)
    assertThat(viewModel.uiState.value.initialLoading).isFalse()
    assertThat(viewModel.uiState.value.previewSnapshot?.workspace?.name).isEqualTo("lab")
    assertThat(viewModel.uiState.value.previewSnapshot?.generatedAtEpochMillis)
        .isEqualTo(1_700_000_000_000)
}
```

Add a second test:

```kotlin
@Test
fun `ensure preview loaded is a no-op when a snapshot already exists`() = runTest {
    val inventory = FakeInventoryRepository()
    val store = FakeModuleLocalStore()
    val viewModel = ReportsViewModel(inventory, store, CapturingBuilder())

    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()
    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()

    assertThat(inventory.currentCalls).isEqualTo(1)
    assertThat(inventory.hostCalls).isEqualTo(1)
    assertThat(inventory.serviceCalls).isEqualTo(1)
    assertThat(inventory.vulnerabilityCalls).isEqualTo(1)
    assertThat(store.historyCalls).isEqualTo(1)
}
```

- [ ] **Step 2: Add atomic refresh failure coverage**

Extend `FakeInventoryRepository` with mutable `workspaceName` and `failServices`. Then add:

```kotlin
@Test
fun `refresh failure preserves the previous complete snapshot`() = runTest {
    val inventory = FakeInventoryRepository()
    val store = FakeModuleLocalStore()
    val viewModel = ReportsViewModel(inventory, store, CapturingBuilder())

    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()
    val original = viewModel.uiState.value.previewSnapshot

    inventory.workspaceName = "new-lab"
    inventory.failServices = true
    viewModel.refreshPreview()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.previewSnapshot).isSameInstanceAs(original)
    assertThat(viewModel.uiState.value.refreshing).isFalse()
    assertThat(viewModel.uiState.value.refreshErrorMessage).isEqualTo("services unavailable")
}
```

Also add an initial failure test:

```kotlin
@Test
fun `initial source failure leaves no snapshot and performs no retry`() = runTest {
    val inventory = FakeInventoryRepository(failServicesInitially = true)
    val store = FakeModuleLocalStore()
    val viewModel = ReportsViewModel(inventory, store, CapturingBuilder())

    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()

    assertThat(inventory.currentCalls).isEqualTo(1)
    assertThat(inventory.hostCalls).isEqualTo(1)
    assertThat(inventory.serviceCalls).isEqualTo(1)
    assertThat(inventory.vulnerabilityCalls).isEqualTo(0)
    assertThat(store.historyCalls).isEqualTo(0)
    assertThat(viewModel.uiState.value.previewSnapshot).isNull()
    assertThat(viewModel.uiState.value.initialLoading).isFalse()
    assertThat(viewModel.uiState.value.refreshErrorMessage).isEqualTo("services unavailable")
}
```

- [ ] **Step 3: Run the focused ViewModel tests and verify they fail**

Run:

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportsViewModelTest'
```

Expected: FAIL because the new state fields and load methods do not exist.

- [ ] **Step 4: Replace `ReportsUiState` with explicit independent operation state**

Use this state shape in `ReportsViewModel.kt`:

```kotlin
data class ReportsUiState(
    val format: ReportFormat = ReportFormat.JSON,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val exporting: Boolean = false,
    val previewSnapshot: ReportPreviewSnapshot? = null,
    val pendingDocument: ReportDocument? = null,
    val refreshErrorMessage: String? = null,
    val exportErrorMessage: String? = null,
    val saveMessage: String? = null,
) {
    val loading: Boolean
        get() = initialLoading || refreshing || exporting
}
```

Keep `loading` as a computed property during Phase 7A so the existing `ReportsScreen` needs only a small compatibility update.

- [ ] **Step 5: Implement initial load and refresh with one private atomic reader**

Add these public methods:

```kotlin
fun ensurePreviewLoaded() {
    val current = _uiState.value
    if (current.previewSnapshot != null || current.initialLoading || current.refreshing) return
    loadPreview(initial = true)
}

fun refreshPreview() {
    val current = _uiState.value
    if (current.initialLoading || current.refreshing || current.exporting) return
    loadPreview(initial = current.previewSnapshot == null)
}
```

Add a private launcher:

```kotlin
private fun loadPreview(initial: Boolean) {
    viewModelScope.launch {
        _uiState.update {
            it.copy(
                initialLoading = initial,
                refreshing = !initial,
                refreshErrorMessage = null,
                saveMessage = null,
            )
        }

        when (val result = readPreviewSnapshot()) {
            is AppResult.Success -> _uiState.update {
                it.copy(
                    initialLoading = false,
                    refreshing = false,
                    previewSnapshot = result.value,
                    refreshErrorMessage = null,
                )
            }
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    initialLoading = false,
                    refreshing = false,
                    refreshErrorMessage = result.error.userMessage,
                )
            }
        }
    }
}
```

Implement `readPreviewSnapshot()` with the same fixed order as the approved design. Return immediately on each `AppResult.Failure`; catch only the local-store exception and convert it to `AppError("EXECUTION_HISTORY_UNAVAILABLE", "無法讀取模組執行紀錄")` without embedding the original exception message:

```kotlin
private suspend fun readPreviewSnapshot(): AppResult<ReportPreviewSnapshot> {
    val workspace = when (val result = inventoryRepository.currentWorkspace()) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> return result
    }
    val hosts = when (val result = inventoryRepository.hosts(workspace.name, RECORD_LIMIT, 0)) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> return result
    }
    val services = when (val result = inventoryRepository.services(workspace.name, RECORD_LIMIT, 0)) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> return result
    }
    val vulnerabilities = when (
        val result = inventoryRepository.vulnerabilities(workspace.name, RECORD_LIMIT, 0)
    ) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> return result
    }
    val executions = try {
        moduleLocalStore.executionHistory(RECORD_LIMIT)
    } catch (_: Exception) {
        return AppResult.Failure(
            AppError("EXECUTION_HISTORY_UNAVAILABLE", "無法讀取模組執行紀錄"),
        )
    }
    return AppResult.Success(
        ReportPreviewSnapshot(
            generatedAtEpochMillis = clock(),
            workspace = workspace,
            hosts = hosts,
            services = services,
            vulnerabilities = vulnerabilities,
            executions = executions,
        ),
    )
}
```

Delete the old behavior where `requestExport()` performs repository calls. Task 3 will reintroduce export using the snapshot.

- [ ] **Step 6: Update the fakes with mutable failure control**

Use constructor state that can be changed after the first successful load:

```kotlin
private class FakeInventoryRepository(
    failServicesInitially: Boolean = false,
) : MetasploitInventoryRepository {
    var workspaceName = "lab"
    var failServices = failServicesInitially
    // existing counters remain

    override suspend fun currentWorkspace(): AppResult<MetasploitWorkspaceSummary> {
        currentCalls += 1
        return AppResult.Success(workspace(name = workspaceName))
    }

    override suspend fun services(
        workspace: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<MetasploitServiceRecord>> {
        serviceCalls += 1
        lastLimit = limit
        lastOffset = offset
        return if (failServices) {
            AppResult.Failure(AppError("SERVICES_UNAVAILABLE", "services unavailable"))
        } else {
            AppResult.Success(emptyList())
        }
    }

    private fun workspace(name: String = workspaceName) = MetasploitWorkspaceSummary(
        id = 1,
        name = name,
        createdAtEpochSeconds = null,
        updatedAtEpochSeconds = null,
        extraFields = emptyMap(),
    )
}
```

- [ ] **Step 7: Run the ViewModel test class and verify it passes**

Run the command from Step 3.

Expected: PASS for all load、no-op and atomic-failure tests.

- [ ] **Step 8: Commit Task 2**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt
git commit -m "feat: load report previews atomically"
```

---

### Task 3: Export exclusively from the current preview snapshot

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
- Modify: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReportPreviewSnapshot.toSafeReportSnapshot()` from Task 1.
- Produces: `requestExport()` that performs zero repository／store calls and creates one `ReportDocument` from the current snapshot.

- [ ] **Step 1: Add a no-snapshot fail-closed test**

```kotlin
@Test
fun `export without a preview snapshot fails closed`() = runTest {
    val inventory = FakeInventoryRepository()
    val store = FakeModuleLocalStore()
    val builder = CapturingBuilder()
    val viewModel = ReportsViewModel(inventory, store, builder)

    viewModel.requestExport()
    advanceUntilIdle()

    assertThat(inventory.currentCalls).isEqualTo(0)
    assertThat(store.historyCalls).isEqualTo(0)
    assertThat(builder.calls).isEqualTo(0)
    assertThat(viewModel.uiState.value.pendingDocument).isNull()
    assertThat(viewModel.uiState.value.exportErrorMessage).isEqualTo("請先載入報告預覽")
}
```

- [ ] **Step 2: Add a loaded-snapshot export test with call-count baselines**

```kotlin
@Test
fun `export uses current snapshot and performs zero additional source reads`() = runTest {
    val inventory = FakeInventoryRepository()
    val store = FakeModuleLocalStore()
    val builder = CapturingBuilder()
    val viewModel = ReportsViewModel(inventory, store, builder) { 1_700_000_000_000 }

    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()
    val sourceCallsBeforeExport = listOf(
        inventory.currentCalls,
        inventory.hostCalls,
        inventory.serviceCalls,
        inventory.vulnerabilityCalls,
        store.historyCalls,
    )

    viewModel.selectFormat(ReportFormat.HTML)
    viewModel.requestExport()
    advanceUntilIdle()

    assertThat(
        listOf(
            inventory.currentCalls,
            inventory.hostCalls,
            inventory.serviceCalls,
            inventory.vulnerabilityCalls,
            store.historyCalls,
        ),
    ).isEqualTo(sourceCallsBeforeExport)
    assertThat(builder.calls).isEqualTo(1)
    assertThat(builder.format).isEqualTo(ReportFormat.HTML)
    assertThat(builder.snapshot?.generatedAtEpochMillis).isEqualTo(1_700_000_000_000)
    assertThat(viewModel.uiState.value.pendingDocument?.fileName).isEqualTo("report.html")
    assertThat(viewModel.uiState.value.exporting).isFalse()
}
```

Update `CapturingBuilder` to save `format`:

```kotlin
var format: ReportFormat? = null

override fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument {
    calls += 1
    this.snapshot = snapshot
    this.format = format
    return ReportDocument(
        id = "report-id",
        format = format,
        fileName = "report.${format.extension}",
        mimeType = format.mimeType,
        bytes = "report".encodeToByteArray(),
    )
}
```

- [ ] **Step 3: Add duplicate-export protection**

Make `CapturingBuilder` optionally block with a `CompletableDeferred<Unit>` and add a test that calls `requestExport()` twice before releasing it. Assert builder calls equals one. Use this exact gate shape:

```kotlin
private class CapturingBuilder(
    private val gate: CompletableDeferred<Unit>? = null,
) : ReportDocumentBuilder {
    // counters and captured values

    override fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument {
        gate?.let { runBlocking { it.await() } }
        // capture and return document
    }
}
```

In the test, run the ViewModel with `StandardTestDispatcher`, call export twice, assert `uiState.value.exporting` is true and builder calls cannot exceed one, then complete the gate and `advanceUntilIdle()`. If blocking the synchronous builder makes the test unnecessarily brittle, replace the builder gate with an injected `documentBuildDispatcher` and a test dispatcher; do not add sleeps.

- [ ] **Step 4: Run focused tests and verify failure before implementation**

Run:

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportsViewModelTest'
```

Expected: FAIL because export still has old source-reading behavior or is temporarily absent after Task 2.

- [ ] **Step 5: Implement memory-only export**

Replace `requestExport()` with:

```kotlin
fun requestExport() {
    val current = _uiState.value
    if (current.exporting || current.initialLoading || current.refreshing) return
    val preview = current.previewSnapshot
    if (preview == null) {
        _uiState.update {
            it.copy(exportErrorMessage = "請先載入報告預覽", saveMessage = null)
        }
        return
    }
    val format = current.format
    viewModelScope.launch {
        _uiState.update {
            it.copy(
                exporting = true,
                pendingDocument = null,
                exportErrorMessage = null,
                saveMessage = null,
            )
        }
        val document = try {
            documentBuilder.build(preview.toSafeReportSnapshot(), format)
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    exporting = false,
                    exportErrorMessage = "無法產生報告",
                )
            }
            return@launch
        }
        _uiState.update {
            it.copy(
                exporting = false,
                pendingDocument = document,
                exportErrorMessage = null,
            )
        }
    }
}
```

Update existing save handlers to write `exportErrorMessage` instead of the removed generic `errorMessage`. Preserve `consumePendingDocument()` semantics.

- [ ] **Step 6: Verify all report feature tests pass**

Run:

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest
```

Expected: PASS with no duplicate source read and no partial export.

- [ ] **Step 7: Commit Task 3**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt
git commit -m "feat: export reports from the preview snapshot"
```

---

### Task 4: Trigger loading on report destination entry and keep the current UI functional

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Interfaces:**
- Consumes: `ReportsViewModel.ensurePreviewLoaded()` and new `ReportsUiState` fields.
- Produces: one destination-entry load request, no recomposition loop, and an export button disabled until a complete snapshot exists.

- [ ] **Step 1: Add the destination-entry callback to `ReportsScreen`**

Change the signature to:

```kotlin
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onEnsurePreviewLoaded: () -> Unit,
    onFormatSelected: (ReportFormat) -> Unit,
    onExport: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onEnsurePreviewLoaded()
    }
    // existing content
}
```

Add the missing `androidx.compose.runtime.LaunchedEffect` import.

- [ ] **Step 2: Update Phase 7A status rendering without implementing Phase 7B lists**

Make these exact compatibility changes in the current screen:

```kotlin
val snapshot = state.previewSnapshot

Text(
    "作用中 Workspace：${snapshot?.workspace?.name ?: "尚未載入"}",
    style = MaterialTheme.typography.labelLarge,
)
if (snapshot != null) {
    Text(
        "Hosts ${snapshot.hosts.size}・Services ${snapshot.services.size}・" +
            "Vulnerabilities ${snapshot.vulnerabilities.size}・執行紀錄 ${snapshot.executions.size}",
        style = MaterialTheme.typography.bodySmall,
    )
}
if (state.initialLoading || state.refreshing || state.exporting) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
}
state.refreshErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
state.exportErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
state.saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
```

Set format chips enabled when `!state.exporting`, and set the export button to:

```kotlin
enabled = snapshot != null && !state.loading
```

Keep the existing include／exclude card. Phase 7B will replace the body with the complete preview UI.

- [ ] **Step 3: Thread the callback through `MagoApp`**

Add to both `MagoApp` and `AppNavHost` parameters:

```kotlin
onReportEnsurePreviewLoaded: () -> Unit,
```

Forward it from `MagoApp` to `AppNavHost`, then pass it into the Reports destination:

```kotlin
ReportsScreen(
    state = reportsState,
    onEnsurePreviewLoaded = onReportEnsurePreviewLoaded,
    onFormatSelected = onReportFormatSelected,
    onExport = onReportExport,
)
```

Do not add a new destination or alter NavigationRail／NavigationBar behavior.

- [ ] **Step 4: Wire the ViewModel method in `MainActivity`**

Add one argument before `onReportFormatSelected`:

```kotlin
onReportEnsurePreviewLoaded = reportsViewModel::ensurePreviewLoaded,
```

Do not change the four SAF launchers or `pendingReport` handling.

- [ ] **Step 5: Run feature tests, app compilation and lint**

Run:

```bash
gradle --no-daemon \
  :feature:reports:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

Expected: PASS. `app/build/outputs/apk/debug/app-debug.apk` exists and lint has no fatal issue.

- [ ] **Step 6: Verify the Termux Bridge bundle remains untouched**

Run:

```bash
git diff --exit-code main...HEAD -- \
  termux-bridge \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt
```

Expected: no diff and exit code 0.

- [ ] **Step 7: Commit Task 4**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt \
  app/src/main/kotlin/dev/mago/android/MagoApp.kt \
  app/src/main/kotlin/dev/mago/android/MainActivity.kt
git commit -m "feat: load report previews on destination entry"
```

---

### Task 5: Run the full Phase 7A verification gate

**Files:**
- Verify only; modify code only when a command exposes a concrete defect.

**Interfaces:**
- Consumes: all deliverables from Tasks 1–4.
- Produces: evidence that Phase 7A is buildable、lint-clean and compatible with existing risk-directed suites.

- [ ] **Step 1: Run all report-related unit tests**

```bash
gradle --no-daemon \
  :core:reporting:testDebugUnitTest \
  :feature:reports:testDebugUnitTest
```

Expected: PASS with zero failures.

- [ ] **Step 2: Run the repository’s complete Android verification command**

```bash
gradle --no-daemon --stacktrace \
  :app:assembleDebug \
  :app:lintDebug \
  :domain:installation:testDebugUnitTest \
  :core:security:testDebugUnitTest \
  :core:termux:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :core:rpc:testDebugUnitTest \
  :core:reporting:testDebugUnitTest \
  :feature:modules:testDebugUnitTest \
  :feature:dashboard:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:reports:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL and zero test／lint failures.

- [ ] **Step 3: Run Bridge contract and reproducibility checks**

```bash
python3 -m pip install --disable-pip-version-check check-jsonschema
bash termux-bridge/tests/contract_test.sh
bash -n termux-bridge/scripts/dispatch.sh \
  termux-bridge/scripts/lib/common.sh \
  termux-bridge/scripts/actions/*.sh
termux-bridge/packaging/build_bundle.sh
git diff --exit-code -- \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt
```

Expected: all commands exit 0 and rebuilding the bundle creates no tracked diff.

- [ ] **Step 4: Inspect the final diff against Phase 7A scope**

```bash
git diff --stat main...HEAD
git diff --check main...HEAD
git status --short
```

Expected:
- only the planned report model、ViewModel、tests and wiring files changed;
- `git diff --check` emits no whitespace error;
- working tree is clean after commits.

- [ ] **Step 5: Create the Phase 7A implementation commit only if verification required fixes**

When fixes were necessary, stage only those exact files and commit:

```bash
git add <exact-files-fixed-after-verification>
git commit -m "fix: complete Phase 7A verification"
```

When no fixes were necessary, do not create an empty commit.

- [ ] **Step 6: Prepare the PR summary with evidence**

The PR body must state:

```markdown
## Goal
Load one complete in-memory report preview and export only from an explicitly sanitized copy of that snapshot.

## Security boundary
- Preview-only fields remain in ViewModel memory.
- `info`, `comments`, `extraFields`, timestamps and other non-export fields are cleared before `ReportDocumentBuilder` receives data.
- Export performs zero additional repository or local-store reads.
- No new RPC method, permission, endpoint, polling or retry.

## Verification
- `:core:reporting:testDebugUnitTest`
- `:feature:reports:testDebugUnitTest`
- full Android Build／Lint／risk-directed test command
- Termux Bridge contract and embedded bundle reproducibility checks
```

Do not claim any command passed unless its fresh output and exit code were inspected in the implementation session.
