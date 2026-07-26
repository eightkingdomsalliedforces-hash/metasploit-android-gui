# MAGO Phase 7B Full Report Preview UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete Reports destination that previews the exact safe export fields by category, supports explicit refresh and category selection, and preserves Phase 7A atomic-load and zero-extra-read guarantees.

**Architecture:** Keep raw `ReportPreviewSnapshot` private inside `ReportsViewModel`. Convert it through the existing sanitizer into a dedicated `ReportPreviewUiModel` before publishing UI state. Render the safe model in one adaptive `LazyColumn`; wire refresh and category selection through the existing app callback chain.

**Tech Stack:** Kotlin, Android ViewModel, Kotlin Coroutines/StateFlow, Jetpack Compose Material 3, JUnit 4, Truth, kotlinx-coroutines-test, Gradle 9.5.0, JDK 17, Android SDK 36.

## Global Constraints

- Start from merged Phase 7A commit `ced80ea4d9df50172af9bbdb86d1224d5e140031`.
- Android minimum API 31; compile SDK 36; Build Tools 36.0.0; JDK 17.
- Keep localhost-only repositories and the 100-record limit for all four categories.
- Add no new RPC method, endpoint, permission, polling, pagination, automatic retry, Credentials, Loot, or Session automation.
- Persist no preview data outside ViewModel memory.
- Keep JSON/CSV/HTML/ZIP SAF launchers, MIME types, and report writer unchanged.
- Produce only Debug APK artifacts.
- Add focused unit tests only; no broad instrumentation or screenshot suite.

---

## File Map

- Create `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewPresentation.kt`
- Create `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewPresentationTest.kt`
- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
- Modify `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`
- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`
- Modify `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

---

### Task 1: Add the safe presentation model

**Files:**
- Create: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewPresentation.kt`
- Create: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewPresentationTest.kt`

**Interfaces:**

```kotlin
enum class ReportPreviewTab(val label: String) {
    HOSTS("Hosts"),
    SERVICES("Services"),
    VULNERABILITIES("弱點"),
    EXECUTIONS("執行紀錄"),
}

data class ReportPreviewUiModel(
    val generatedAtEpochMillis: Long,
    val workspaceName: String,
    val hosts: List<ReportHostPreviewItem>,
    val services: List<ReportServicePreviewItem>,
    val vulnerabilities: List<ReportVulnerabilityPreviewItem>,
    val executions: List<ReportExecutionPreviewItem>,
)

fun ReportPreviewSnapshot.toUiModel(): ReportPreviewUiModel
```

- [ ] **Step 1: Write the failing presentation conversion test**

Create a populated `ReportPreviewSnapshot` with sentinel values in MAC, info, comments, extraFields, vulnerability resource, module result, and module error. Call `toUiModel()` and assert:

```kotlin
assertThat(model.workspaceName).isEqualTo("lab")
assertThat(model.hosts.single()).isEqualTo(
    ReportHostPreviewItem(
        address = "192.0.2.10",
        name = "target",
        state = "alive",
        operatingSystem = "Linux",
        operatingSystemFlavor = "Ubuntu",
        purpose = "server",
    ),
)
assertThat(model.services.single().name).isEqualTo("https")
assertThat(model.vulnerabilities.single().references).containsExactly("CVE-TEST")
assertThat(model.executions.single().redactedOptions)
    .containsExactly("PASSWORD", "[REDACTED]")
```

The UI item classes deliberately have no properties for excluded fields.

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportPreviewPresentationTest'
```

Expected: compilation failure because the presentation types and `toUiModel()` do not exist.

- [ ] **Step 3: Implement the presentation types and conversion**

`toUiModel()` must begin with:

```kotlin
val safe = toSafeReportSnapshot()
```

Map only the safe snapshot fields into immutable item models. Copy `redactedOptions` with `toMap()` and references with `toList()`.

- [ ] **Step 4: Run focused and report tests**

```bash
gradle --no-daemon \
  :core:reporting:testDebugUnitTest \
  :feature:reports:testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 5: Commit Task 1**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewPresentation.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportPreviewPresentationTest.kt
git commit -m "feat: add safe report preview presentation models"
```

---

### Task 2: Publish safe preview state and category selection

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt`
- Modify: `feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt`

**Interfaces:**

```kotlin
data class ReportsUiState(
    val format: ReportFormat = ReportFormat.JSON,
    val selectedPreviewTab: ReportPreviewTab = ReportPreviewTab.HOSTS,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val exporting: Boolean = false,
    val preview: ReportPreviewUiModel? = null,
    val pendingDocument: ReportDocument? = null,
    val refreshErrorMessage: String? = null,
    val exportErrorMessage: String? = null,
    val saveMessage: String? = null,
)

fun selectPreviewTab(tab: ReportPreviewTab)
```

The ViewModel owns:

```kotlin
private var rawPreviewSnapshot: ReportPreviewSnapshot? = null
```

- [ ] **Step 1: Update the load test to expect safe public state**

After `ensurePreviewLoaded()` and `advanceUntilIdle()`:

```kotlin
assertThat(viewModel.uiState.value.preview?.workspaceName).isEqualTo("lab")
assertThat(viewModel.uiState.value.preview?.generatedAtEpochMillis)
    .isEqualTo(1_700_000_000_000)
```

Replace all `previewSnapshot` assertions with `preview` assertions.

- [ ] **Step 2: Add a category-selection test**

```kotlin
@Test
fun `selecting preview category changes only ui state`() = runTest {
    val inventory = FakeInventoryRepository()
    val store = FakeModuleLocalStore()
    val viewModel = ReportsViewModel(inventory, store, CapturingBuilder())
    viewModel.ensurePreviewLoaded()
    advanceUntilIdle()
    val callsBefore = listOf(
        inventory.currentCalls,
        inventory.hostCalls,
        inventory.serviceCalls,
        inventory.vulnerabilityCalls,
        store.historyCalls,
    )

    viewModel.selectPreviewTab(ReportPreviewTab.EXECUTIONS)

    assertThat(viewModel.uiState.value.selectedPreviewTab)
        .isEqualTo(ReportPreviewTab.EXECUTIONS)
    assertThat(
        listOf(
            inventory.currentCalls,
            inventory.hostCalls,
            inventory.serviceCalls,
            inventory.vulnerabilityCalls,
            store.historyCalls,
        ),
    ).isEqualTo(callsBefore)
}
```

- [ ] **Step 3: Run the ViewModel test and confirm RED**

```bash
gradle --no-daemon :feature:reports:testDebugUnitTest \
  --tests 'dev.mago.android.reports.ReportsViewModelTest'
```

Expected: failure because `preview`, `selectedPreviewTab`, and `selectPreviewTab()` do not exist.

- [ ] **Step 4: Refactor the ViewModel**

On successful load, perform the private assignment before publishing state:

```kotlin
rawPreviewSnapshot = result.value
_uiState.update {
    it.copy(
        initialLoading = false,
        refreshing = false,
        preview = result.value.toUiModel(),
        refreshErrorMessage = null,
    )
}
```

On refresh failure, do not replace `rawPreviewSnapshot` or `preview`.

Export must read:

```kotlin
val preview = rawPreviewSnapshot
```

and continue calling:

```kotlin
documentBuilder.build(preview.toSafeReportSnapshot(), format)
```

Implement category selection as one synchronous state update with no repository call.

- [ ] **Step 5: Run all report tests**

```bash
gradle --no-daemon \
  :core:reporting:testDebugUnitTest \
  :feature:reports:testDebugUnitTest
```

Expected: all tests pass, including existing zero-extra-read export coverage.

- [ ] **Step 6: Commit Task 2**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsViewModel.kt \
  feature/reports/src/test/kotlin/dev/mago/android/reports/ReportsViewModelTest.kt
git commit -m "feat: publish safe report preview state"
```

---

### Task 3: Build the complete adaptive preview screen

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Interfaces:**

```kotlin
fun ReportsScreen(
    state: ReportsUiState,
    onEnsurePreviewLoaded: () -> Unit,
    onRefreshPreview: () -> Unit,
    onPreviewTabSelected: (ReportPreviewTab) -> Unit,
    onFormatSelected: (ReportFormat) -> Unit,
    onExport: () -> Unit,
)
```

- [ ] **Step 1: Replace the root Column with one LazyColumn**

Use `Modifier.fillMaxSize().padding(16.dp)` and `Arrangement.spacedBy(12.dp)`. Keep `LaunchedEffect(Unit)` for the initial load.

- [ ] **Step 2: Add header, refresh, and state messages**

The refresh button is enabled when neither `initialLoading`, `refreshing`, nor `exporting` is true. Initial load shows `正在建立報告預覽…`; refresh keeps existing cards visible and shows `正在重新整理預覽…`.

- [ ] **Step 3: Add summary and safety cards**

Display Workspace, localized generation time, all four counts, the 100-record cap, and the exact excluded-data notice from the design.

- [ ] **Step 4: Add format and category chips**

Both rows use `horizontalScroll(rememberScrollState())`. Category chip labels include counts, for example `Hosts 12` and `執行紀錄 4`.

- [ ] **Step 5: Render category cards**

Use `items(..., key = ...)` with stable keys:

```kotlin
host.address
"${service.host}:${service.port}/${service.protocol}"
"${vulnerability.host}:${vulnerability.port}:${vulnerability.name}"
execution.correlationId
```

Render only non-empty optional fields. Execution options appear as `key：value`, sorted by key for stable output.

- [ ] **Step 6: Add category-specific empty messages and export action**

Do not show an empty message during initial load or when refresh failed before any preview existed. Keep export disabled until `state.preview != null && !state.loading`.

- [ ] **Step 7: Thread callbacks through app wiring**

Add to `MagoApp` and `AppNavHost`:

```kotlin
onReportRefreshPreview: () -> Unit
onReportPreviewTabSelected: (ReportPreviewTab) -> Unit
```

Wire in `MainActivity`:

```kotlin
onReportRefreshPreview = reportsViewModel::refreshPreview,
onReportPreviewTabSelected = reportsViewModel::selectPreviewTab,
```

Do not modify App-lock, theme, font-scale, reduced-motion, pending report handling, SAF launchers, or MIME types.

- [ ] **Step 8: Build, lint, and run report tests**

```bash
gradle --no-daemon \
  :feature:reports:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

Expected: success and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 9: Commit Task 3**

```bash
git add \
  feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt \
  app/src/main/kotlin/dev/mago/android/MagoApp.kt \
  app/src/main/kotlin/dev/mago/android/MainActivity.kt
git commit -m "feat: add complete report preview interface"
```

---

### Task 4: Run the Phase 7B verification gate

- [ ] **Step 1: Run the complete Android verification command**

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

- [ ] **Step 2: Run Bridge contract and bundle reproducibility checks**

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

- [ ] **Step 3: Review scope**

```bash
git diff --stat main...HEAD
git diff --check main...HEAD
git status --short
```

Expected: only the two docs and seven planned implementation/test files changed; no whitespace error; clean working tree.

- [ ] **Step 4: Prepare a PR**

The PR must describe the private raw snapshot boundary, safe public presentation model, four preview categories, responsive behavior, explicit refresh, unchanged SAF export flow, focused tests, and fresh CI evidence.