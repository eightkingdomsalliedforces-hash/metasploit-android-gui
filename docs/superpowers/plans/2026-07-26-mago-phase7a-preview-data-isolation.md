# MAGO Phase 7A Preview Data Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one atomic in-memory report preview snapshot and ensure every export is built from an explicitly sanitized copy of that same snapshot without additional repository reads.

**Architecture:** `feature:reports` owns the preview model, state machine, and preview-to-export conversion. `ReportsViewModel` reads Workspace、Hosts、Services、Vulnerabilities and local execution history in one bounded sequence, replaces state only after all sources succeed, and exports from memory. `app` only wires destination entry and existing SAF launchers.

**Tech Stack:** Kotlin, Android ViewModel, Kotlin Coroutines／StateFlow, Jetpack Compose, JUnit 4, Truth, kotlinx-coroutines-test, Gradle 9.5.0, JDK 17, Android SDK 36.

## Global Constraints

- Start implementation from the latest `main`; never use PR #12 or `feature/phase4-jobs-sessions` as the base.
- Android minimum remains API 31; compile SDK and Build Tools remain 36／36.0.0.
- Keep RPC access limited to existing localhost repository methods; add no Credentials、Loot、Session automation、new endpoint、polling or automatic retry.
- Preview data may exist only in `ReportsViewModel` memory. Do not put it in Room、SavedStateHandle、Logcat、exception text、analytics or CI artifacts.
- Read at most 100 Hosts、100 Services、100 Vulnerabilities and 100 execution records at offset 0.
- Safe exports must exclude preview-only fields before `ReportDocumentBuilder` receives the snapshot, not merely rely on individual format builders ignoring them.
- Preserve the existing SAF launchers, MIME types and document writer behavior.
- Use test-first development and commit after every independently reviewable task.

---

## File Map

- Create `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewModels.kt`
  - `ReportPreviewSnapshot`
  - `ReportPreviewSnapshot.toSafeReportSnapshot()`
- Create `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewModelsTest.kt`
  - Conversion and field-removal tests.
- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
  - Initial load、manual refresh、memory-only export and independent operation flags.
- Modify `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`
  - Atomic load、failure preservation、duplicate-operation and zero-extra-read tests.
- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`
  - One-time destination-entry callback and minimal Phase 7A status display.
- Modify `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
  - Callback plumbing only.
- Modify `app/src/main/kotlin/dev/mago/android/MainActivity.kt`
  - Wire `reportsViewModel::ensurePreviewLoaded`; keep SAF code unchanged.

---

### Task 1: Create the preview model and safe conversion

**Files:**
- Create: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewModels.kt`
- Create: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewModelsTest.kt`

**Interfaces:**
- Consumes: existing Inventory record types, `ModuleExecutionRecord`, and `ReportSnapshot`.
- Produces:

```kotlin
data class ReportPreviewSnapshot(
    val generatedAtEpochMillis: Long,
    val workspace: MetasploitWorkspaceSummary,
    val hosts: List<MetasploitHostRecord>,
    val services: List<MetasploitServiceRecord>,
    val vulnerabilities: List<MetasploitVulnerabilityRecord>,
    val executions: List<ModuleExecutionRecord>,
)

fun ReportPreviewSnapshot.toSafeReportSnapshot(): ReportSnapshot
```

- [ ] **Step 1: Write the failing sanitizer test**

Create `ReportPreviewModelsTest.kt`:

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
    fun `safe snapshot preserves exported fields and clears preview-only fields`() {
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
            executions = listOf(
                ModuleExecutionRecord(
                    correlationId = "correlation-1",
                    action = MetasploitModuleRunAction.CHECK,
                    type = MetasploitModuleType.AUXILIARY,
                    name = "scanner/test",
                    status = MetasploitModuleRunStatus.READY,
                    jobId = 9,
                    uuid = "uuid-1",
                    redactedOptions = mapOf("PASSWORD" to "[REDACTED]"),
                    resultSummary = "RESULT_SECRET",
                    error = "ERROR_SECRET",
                    createdAtEpochMillis = 1_700_000_000_000,
                    updatedAtEpochMillis = 1_700_000_000_100,
                ),
            ),
        )

        val safe = preview.toSafeReportSnapshot()

        assertThat(safe.generatedAtEpochMillis).isEqualTo(1_700_000_000_000)
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

        val execution = safe.executions.single()
        assertThat(execution.correlationId).isEqualTo("correlation-1")
        assertThat(execution.jobId).isEqualTo(9)
        assertThat(execution.uuid).isEqualTo("uuid-1")
        assertThat(execution.redactedOptions).containsExactly("PASSWORD", "[REDACTED]")
        assertThat(execution.resultSummary).isNull()
        assertThat(execution.error).isNull()
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the red state**

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportPreviewModelsTest'
```

Expected: compilation fails because `ReportPreviewSnapshot` and `toSafeReportSnapshot()` do not exist.

- [ ] **Step 3: Add the minimal preview model and sanitizer**

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
    executions = executions.map { execution ->
        execution.copy(
            resultSummary = null,
            error = null,
        )
    },
)
```

- [ ] **Step 4: Run the focused test and confirm the green state**

Run the command from Step 2.

Expected: one passing test.

- [ ] **Step 5: Run all report-format tests**

```bash
gradle --no-daemon \
  :core:reporting:testDebugUnitTest \
  :feature:reports:testDebugUnitTest
```

Expected: all report tests pass.

- [ ] **Step 6: Commit Task 1**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewModels.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewModelsTest.kt
git commit -m "feat: isolate report preview data from exports"
```

---

### Task 2: Add atomic initial loading and manual refresh

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
- Modify: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReportPreviewSnapshot`.
- Produces:

```kotlin
fun ensurePreviewLoaded()
fun refreshPreview()
```

and this state shape:

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

- [ ] **Step 1: Replace the old direct-export load test**

Add:

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
    assertThat(viewModel.uiState.value.previewSnapshot?.workspace?.name).isEqualTo("lab")
    assertThat(viewModel.uiState.value.previewSnapshot?.generatedAtEpochMillis)
        .isEqualTo(1_700_000_000_000)
    assertThat(viewModel.uiState.value.initialLoading).isFalse()
}
```

- [ ] **Step 2: Add an in-flight duplicate-load test**

Extend `FakeInventoryRepository` with an optional gate:

```kotlin
private class FakeInventoryRepository(
    failServicesInitially: Boolean = false,
    private val currentWorkspaceGate: CompletableDeferred<Unit>? = null,
) : MetasploitInventoryRepository {
    var workspaceName = "lab"
    var failServices = failServicesInitially

    override suspend fun currentWorkspace(): AppResult<MetasploitWorkspaceSummary> {
        currentCalls += 1
        currentWorkspaceGate?.await()
        return AppResult.Success(workspace(workspaceName))
    }

    private fun workspace(name: String) = MetasploitWorkspaceSummary(
        id = 1,
        name = name,
        createdAtEpochSeconds = null,
        updatedAtEpochSeconds = null,
        extraFields = emptyMap(),
    )
}
```

Add:

```kotlin
@Test
fun `two initial load requests while the first is suspended produce one source read`() = runTest {
    val gate = CompletableDeferred<Unit>()
    val inventory = FakeInventoryRepository(currentWorkspaceGate = gate)
    val viewModel = ReportsViewModel(inventory, FakeModuleLocalStore(), CapturingBuilder())

    viewModel.ensurePreviewLoaded()
    viewModel.ensurePreviewLoaded()

    assertThat(viewModel.uiState.value.initialLoading).isTrue()
    assertThat(inventory.currentCalls).isEqualTo(1)

    gate.complete(Unit)
    advanceUntilIdle()

    assertThat(inventory.currentCalls).isEqualTo(1)
    assertThat(viewModel.uiState.value.previewSnapshot).isNotNull()
}
```

- [ ] **Step 3: Add initial-failure and refresh-preservation tests**

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
    assertThat(viewModel.uiState.value.refreshErrorMessage).isEqualTo("services unavailable")
}

@Test
fun `refresh failure preserves the previous complete snapshot`() = runTest {
    val inventory = FakeInventoryRepository()
    val viewModel = ReportsViewModel(inventory, FakeModuleLocalStore(), CapturingBuilder())

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

- [ ] **Step 4: Run the focused ViewModel tests and confirm failure**

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportsViewModelTest'
```

Expected: tests fail because the new methods and state fields do not exist.

- [ ] **Step 5: Implement synchronous operation guards**

Set the operation flag before launching the coroutine so two same-frame requests cannot both start:

```kotlin
fun ensurePreviewLoaded() {
    val current = _uiState.value
    if (current.previewSnapshot != null || current.initialLoading || current.refreshing) return
    beginPreviewLoad(initial = true)
}

fun refreshPreview() {
    val current = _uiState.value
    if (current.initialLoading || current.refreshing || current.exporting) return
    beginPreviewLoad(initial = current.previewSnapshot == null)
}

private fun beginPreviewLoad(initial: Boolean) {
    _uiState.update {
        it.copy(
            initialLoading = initial,
            refreshing = !initial,
            refreshErrorMessage = null,
            saveMessage = null,
        )
    }
    viewModelScope.launch {
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

- [ ] **Step 6: Implement the fixed-order atomic reader**

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

Do not include the caught exception message in `AppError`.

- [ ] **Step 7: Update `FakeInventoryRepository.services()`**

```kotlin
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
```

Keep all existing interface methods and counters.

- [ ] **Step 8: Run the focused tests and confirm success**

Run the command from Step 4.

Expected: all `ReportsViewModelTest` load and refresh tests pass.

- [ ] **Step 9: Commit Task 2**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt
git commit -m "feat: load report previews atomically"
```

---

### Task 3: Export only from the current preview snapshot

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
- Modify: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReportPreviewSnapshot.toSafeReportSnapshot()`.
- Produces: `requestExport()` with zero repository or local-store reads.

- [ ] **Step 1: Add no-snapshot and zero-extra-read tests**

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

@Test
fun `export uses the current snapshot without additional source reads`() = runTest {
    val inventory = FakeInventoryRepository()
    val store = FakeModuleLocalStore()
    val builder = CapturingBuilder()
    val viewModel = ReportsViewModel(inventory, store, builder) { 1_700_000_000_000 }

    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()
    val before = listOf(
        inventory.currentCalls,
        inventory.hostCalls,
        inventory.serviceCalls,
        inventory.vulnerabilityCalls,
        store.historyCalls,
    )

    viewModel.selectFormat(ReportFormat.HTML)
    viewModel.requestExport()
    advanceUntilIdle()

    val after = listOf(
        inventory.currentCalls,
        inventory.hostCalls,
        inventory.serviceCalls,
        inventory.vulnerabilityCalls,
        store.historyCalls,
    )
    assertThat(after).isEqualTo(before)
    assertThat(builder.calls).isEqualTo(1)
    assertThat(builder.format).isEqualTo(ReportFormat.HTML)
    assertThat(builder.snapshot?.generatedAtEpochMillis).isEqualTo(1_700_000_000_000)
    assertThat(viewModel.uiState.value.pendingDocument?.fileName).isEqualTo("report.html")
}
```

- [ ] **Step 2: Add deterministic duplicate-export coverage**

Add `StandardTestDispatcher` import and this test:

```kotlin
@Test
fun `two export requests before the builder runs create one document`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    val builder = CapturingBuilder()
    val viewModel = ReportsViewModel(
        FakeInventoryRepository(),
        FakeModuleLocalStore(),
        builder,
    )

    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()

    viewModel.requestExport()
    viewModel.requestExport()

    assertThat(viewModel.uiState.value.exporting).isTrue()
    advanceUntilIdle()

    assertThat(builder.calls).isEqualTo(1)
    assertThat(viewModel.uiState.value.exporting).isFalse()
}
```

The test is deterministic because `requestExport()` must set `exporting = true` synchronously before launching document creation. Do not use sleeps、`runBlocking` or latch-based builder blocking.

- [ ] **Step 3: Update `CapturingBuilder`**

```kotlin
private class CapturingBuilder : ReportDocumentBuilder {
    var calls = 0
    var snapshot: ReportSnapshot? = null
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
}
```

- [ ] **Step 4: Run focused tests and confirm failure**

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportsViewModelTest'
```

Expected: export tests fail until old source-reading behavior is removed.

- [ ] **Step 5: Implement synchronous export guarding and memory-only generation**

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
    _uiState.update {
        it.copy(
            exporting = true,
            pendingDocument = null,
            exportErrorMessage = null,
            saveMessage = null,
        )
    }
    viewModelScope.launch {
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

Update:
- `selectFormat()` to ignore changes only while `exporting` is true.
- `onSaveFailed()` to set `exportErrorMessage` and clear `saveMessage`.
- `onSaveCompleted()` and `onPickerCancelled()` to clear `exportErrorMessage`.
- `consumePendingDocument()` without changing its ID matching behavior.

Delete the old repository reads from `requestExport()`.

- [ ] **Step 6: Run all report feature tests**

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest
```

Expected: all tests pass; duplicate export produces one builder call.

- [ ] **Step 7: Commit Task 3**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt
git commit -m "feat: export reports from the preview snapshot"
```

---

### Task 4: Trigger initial loading from the report destination

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Interfaces:**
- Consumes: `ReportsViewModel.ensurePreviewLoaded()` and the new state.
- Produces: one entry callback and a functional interim report screen before Phase 7B.

- [ ] **Step 1: Add the one-time callback to `ReportsScreen`**

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
    // retain the current report layout below this point
}
```

Import `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 2: Adapt the interim status display**

Inside the existing screen, define:

```kotlin
val snapshot = state.previewSnapshot
```

Replace the current Workspace／progress／error block with:

```kotlin
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
if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
state.refreshErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
state.exportErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
state.saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
```

Set format chips to `enabled = !state.exporting`.

Set the export button to:

```kotlin
enabled = snapshot != null && !state.loading
```

Keep the existing include／exclude card. Do not add the full record lists in Phase 7A.

- [ ] **Step 3: Add callback plumbing to `MagoApp` and `AppNavHost`**

Add this parameter to both functions:

```kotlin
onReportEnsurePreviewLoaded: () -> Unit,
```

Forward it through `MagoApp` into `AppNavHost`, then change the Reports destination to:

```kotlin
ReportsScreen(
    state = reportsState,
    onEnsurePreviewLoaded = onReportEnsurePreviewLoaded,
    onFormatSelected = onReportFormatSelected,
    onExport = onReportExport,
)
```

Do not add or rename a navigation destination.

- [ ] **Step 4: Wire `MainActivity`**

Add this argument immediately before `onReportFormatSelected`:

```kotlin
onReportEnsurePreviewLoaded = reportsViewModel::ensurePreviewLoaded,
```

Do not modify `pendingReport`, the four `CreateDocument` launchers, MIME types or `handleReportDestination()`.

- [ ] **Step 5: Compile and lint**

```bash
gradle --no-daemon \
  :feature:reports:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

Expected: all tasks succeed and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 6: Confirm no Termux Bridge change entered the slice**

```bash
git diff --exit-code main...HEAD -- \
  termux-bridge \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt
```

Expected: exit code 0 with no diff.

- [ ] **Step 7: Commit Task 4**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt \
  app/src/main/kotlin/dev/mago/android/MagoApp.kt \
  app/src/main/kotlin/dev/mago/android/MainActivity.kt
git commit -m "feat: load report previews on destination entry"
```

---

### Task 5: Run the Phase 7A verification gate

**Files:**
- Verify the files changed in Tasks 1–4.

**Interfaces:**
- Consumes: the completed Phase 7A slice.
- Produces: fresh build、test、lint and Bridge verification evidence.

- [ ] **Step 1: Run report tests**

```bash
gradle --no-daemon \
  :core:reporting:testDebugUnitTest \
  :feature:reports:testDebugUnitTest
```

Expected: zero failures.

- [ ] **Step 2: Run the repository Android verification command**

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

Expected: `BUILD SUCCESSFUL` and zero test or lint failures.

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

Expected: every command exits 0 and bundle regeneration leaves no tracked diff.

- [ ] **Step 4: Inspect scope and whitespace**

```bash
git diff --stat main...HEAD
git diff --check main...HEAD
git status --short
```

Expected:
- changed files are limited to the seven planned implementation／test files;
- `git diff --check` prints no whitespace error;
- the working tree is clean after commits.

- [ ] **Step 5: Commit verification fixes only when required**

When verification exposes a defect, make the smallest correction, then stage the complete Phase 7A file set so no untracked correction is omitted:

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewModels.kt \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewModelsTest.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt \
  app/src/main/kotlin/dev/mago/android/MagoApp.kt \
  app/src/main/kotlin/dev/mago/android/MainActivity.kt
git commit -m "fix: complete Phase 7A verification"
```

When verification requires no correction, do not create an empty commit.

- [ ] **Step 6: Prepare the PR body**

Use:

```markdown
## Goal
Load one complete in-memory report preview and export only from an explicitly sanitized copy of that snapshot.

## Security boundary
- Preview-only fields remain in ViewModel memory.
- `info`, `comments`, `extraFields`, non-export timestamps, module result summaries and raw errors are cleared before `ReportDocumentBuilder` receives data.
- Export performs zero additional repository or local-store reads.
- No new RPC method, permission, endpoint, polling or retry.

## Verification
- `:core:reporting:testDebugUnitTest`
- `:feature:reports:testDebugUnitTest`
- full Android Build／Lint／risk-directed test command
- Termux Bridge contract and embedded bundle reproducibility checks
```

Only mark a verification item successful after reading its fresh exit code and output.
