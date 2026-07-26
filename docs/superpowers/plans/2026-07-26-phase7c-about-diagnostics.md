# MAGO Phase 7C About and Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the existing Diagnostics destination into a privacy-preserving About and support surface with a stable, manually copied, strictly whitelisted diagnostic summary.

**Architecture:** The App module reads build/platform constants and current passive `BootstrapCoordinator` values, then creates a feature-owned primitive `DiagnosticsPresentationInput`. The Diagnostics feature owns a pure presenter and summary builder with exact allowlists and fail-closed deny rules; Compose renders `DiagnosticsUiModel`, while the App layer alone writes the summary to Android ClipboardManager and returns a Boolean result.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android ClipboardManager, JUnit 4, Truth, Gradle 9.5.0, JDK 17, Android SDK 36.

## Global Constraints

- Start implementation from `main` commit `31b4a64234585090169157acda0895d983959072`.
- Android minimum API 31; compile SDK 36; Build Tools 36.0.0; JDK 17.
- Keep About inside the existing Diagnostics route; add no navigation destination.
- Do not collect or expose device brand, model, serial, Android ID, advertising ID, installer identity, or another device identifier.
- Include installation stage, last successful stage, failure kind, and `AppError.errorCode`; exclude `userMessage`, `diagnosticData`, stack traces, raw exceptions, operation IDs, retry counters, raw Bridge output, and complete paths.
- Add no RPC method, Bridge action, endpoint, permission, persistence, analytics, upload, background work, polling, automatic retry, share/email intent, file export, update check, or release download.
- Entering Diagnostics performs no coordinator action, repository read, Bridge command, or RPC request.
- Clipboard copying is manual only and uses label `MAGO diagnostics`.
- Clipboard errors return fixed UI text and never log copied content or exception text.
- Display and copied Bridge values use the same exact ordered allowlist; unknown future keys fail closed.
- Run focused unit tests only; add no broad screenshot or instrumentation suite.

---

## File Map

- Create `feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentation.kt`
- Create `feature/diagnostics/src/test/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentationTest.kt`
- Modify `feature/diagnostics/build.gradle.kts`
- Modify `feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsScreen.kt`
- Create `app/src/main/kotlin/dev/mago/android/DiagnosticsClipboard.kt`
- Create `app/src/test/kotlin/dev/mago/android/DiagnosticsClipboardTest.kt`
- Modify `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify `app/src/main/kotlin/dev/mago/android/MainActivity.kt`
- Modify `.github/workflows/android.yml`
- Modify `README.md`

---

### Task 1: Pure presentation model and strict diagnostic summary

**Files:**
- Create: `feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentation.kt`
- Create: `feature/diagnostics/src/test/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentationTest.kt`
- Modify: `feature/diagnostics/build.gradle.kts`

**Produces:**

```kotlin
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

object DiagnosticsPresenter {
    fun present(input: DiagnosticsPresentationInput): DiagnosticsUiModel
}
```

- [ ] **Step 1: Add unit-test dependencies**

Add:

```kotlin
testImplementation(libs.junit)
testImplementation(libs.truth)
```

- [ ] **Step 2: Write failing privacy and ordering tests**

Create tests that assert the summary contains:

```kotlin
assertThat(summary).contains("App: 0.7.0 (7)")
assertThat(summary).contains("Android: 16 / API 36")
assertThat(summary).contains("ABI: arm64-v8a")
assertThat(summary).contains("Bridge: v2")
assertThat(summary).contains("Metasploit: 6.4.99")
assertThat(summary).contains("Installation stage: READY")
assertThat(summary).contains("Error code: RPC_UNAVAILABLE")
assertThat(summary).contains("RPC localhost: true")
```

Use sentinel entries and assert the summary does not contain:

```kotlin
"Pixel", "SERIAL_SECRET", "ANDROID_ID_SECRET", "PASSWORD_SECRET",
"TOKEN_SECRET", "/data/data/secret", "RAW_EXCEPTION_SECRET", "198.51.100.8"
```

Add separate tests for exact allowlist matching, sensitive entries, all deny fragments, last-value-wins duplicate handling, localhost values (`127.0.0.1`, `::1`, case-insensitive `localhost`), remote-host false without address exposure, fixed key order, and missing values producing `unknown`/`none`.

- [ ] **Step 3: Run focused test and confirm RED**

```bash
gradle --no-daemon :feature:diagnostics:testDebugUnitTest \
  --tests 'dev.mago.android.diagnostics.DiagnosticsPresentationTest'
```

Expected: test compilation fails because the presentation API is absent.

- [ ] **Step 4: Implement exact allowlist and deny rules**

Use this ordered allowlist:

```kotlin
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
```

Normalize keys by lowercasing and removing `_`, `-`, and `.`, then reject any key containing:

```kotlin
private val deniedKeyFragments = setOf(
    "password", "token", "credential", "secret", "path",
    "prefix", "serial", "deviceid", "androidid",
)
```

Reduce duplicate keys before filtering, keeping the final entry. Exclude `sensitive=true`. Never display/copy `rpcHost`; derive localhost true only for `127.0.0.1`, `::1`, or case-insensitive `localhost`.

- [ ] **Step 5: Emit the approved fixed summary**

Emit all fields in the approved order, ending with:

```text
Privacy:
- Device brand/model omitted
- Device identifiers omitted
- Credentials/tokens omitted
- Paths and raw errors omitted
- This report is copied manually and is never uploaded automatically
```

- [ ] **Step 6: Run test and confirm GREEN**

```bash
gradle --no-daemon :feature:diagnostics:testDebugUnitTest
```

- [ ] **Step 7: Commit**

```bash
git add feature/diagnostics
git commit -m "feat: add privacy-safe diagnostics presentation"
```

---

### Task 2: Complete About and Diagnostics Compose interface

**Files:**
- Modify: `feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsScreen.kt`

**Consumes:** `DiagnosticsUiModel`

**Produces:**

```kotlin
@Composable
fun DiagnosticsScreen(
    uiModel: DiagnosticsUiModel,
    onCopySummary: (String) -> Boolean,
)
```

- [ ] **Step 1: Replace the page with one LazyColumn**

Use `Modifier.fillMaxSize().padding(16.dp)` and `Arrangement.spacedBy(12.dp)`. Show `診斷資訊` and `診斷資料只會在你按下複製後寫入系統剪貼簿，不會自動上傳。`.

- [ ] **Step 2: Add About card**

Show MAGO version/code, Bridge version, `bridgeSha256.take(12)` plus ellipsis, and `Android 12 / API 31 以上`.

- [ ] **Step 3: Add system/installation card**

Show Android release/API, primary ABI, Metasploit version, current stage, last successful stage, failure kind, and safe error code. Render missing optional platform values as `尚未取得`; absent failure/error as `無`.

- [ ] **Step 4: Add Bridge status list**

Render only `uiModel.bridgeEntries`, preserving presenter order. Never accept or iterate the original raw entry list.

- [ ] **Step 5: Add manual copy state**

```kotlin
enum class DiagnosticsCopyStatus { SUCCESS, FAILURE }
var copyStatus by remember { mutableStateOf<DiagnosticsCopyStatus?>(null) }
```

Set status from `onCopySummary(uiModel.copySummary)`. Show `已複製診斷摘要` or `無法複製診斷摘要` below a full-width `複製已遮罩的診斷摘要` button. Do not store the summary in local UI state.

- [ ] **Step 6: Compile and commit**

```bash
gradle --no-daemon :feature:diagnostics:compileDebugKotlin
git add feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsScreen.kt
git commit -m "feat: add About and diagnostic support screen"
```

---

### Task 3: App platform wiring and clipboard boundary

**Files:**
- Create: `app/src/main/kotlin/dev/mago/android/DiagnosticsClipboard.kt`
- Create: `app/src/test/kotlin/dev/mago/android/DiagnosticsClipboardTest.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Produces:**

```kotlin
internal fun tryWriteDiagnosticsClipboard(write: () -> Unit): Boolean
```

- [ ] **Step 1: Write failing clipboard tests**

```kotlin
@Test
fun `clipboard helper returns true when writer succeeds`() {
    assertThat(tryWriteDiagnosticsClipboard {}).isTrue()
}

@Test
fun `clipboard helper returns false when writer throws`() {
    assertThat(tryWriteDiagnosticsClipboard { error("RAW_EXCEPTION") }).isFalse()
}
```

- [ ] **Step 2: Run test and confirm RED**

```bash
gradle --no-daemon :app:testDebugUnitTest \
  --tests 'dev.mago.android.DiagnosticsClipboardTest'
```

- [ ] **Step 3: Implement fail-closed clipboard helper**

```kotlin
internal fun tryWriteDiagnosticsClipboard(write: () -> Unit): Boolean =
    try {
        write()
        true
    } catch (_: Exception) {
        false
    }
```

Do not log the exception or content.

- [ ] **Step 4: Construct passive DiagnosticsPresentationInput**

Collect only existing flows already exposed by the coordinator:

```kotlin
val metasploitVersion by container.bootstrapCoordinator.metasploitVersion
    .collectAsStateWithLifecycle()
val diagnostics by container.bootstrapCoordinator.diagnostics
    .collectAsStateWithLifecycle()
```

Use the already collected `installationState`. Construct:

```kotlin
val diagnosticsUiModel = DiagnosticsPresenter.present(
    DiagnosticsPresentationInput(
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        minimumApi = 31,
        bridgeVersion = BridgeBundleMetadata.VERSION,
        bridgeSha256 = BridgeBundleMetadata.SHA256,
        androidRelease = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
        primaryAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        metasploitVersion = metasploitVersion?.frameworkVersion,
        currentStage = installationState.stage.name,
        lastSuccessfulStage = installationState.lastSuccessfulStage?.name,
        failureKind = installationState.failureKind?.name,
        errorCode = installationState.lastError?.errorCode,
        diagnosticEntries = diagnostics,
    ),
)
```

Do not collect the environment flow; none of its fields are approved inputs.

- [ ] **Step 5: Add platform clipboard callback**

```kotlin
onCopyDiagnosticsSummary = { summary ->
    tryWriteDiagnosticsClipboard {
        val clipboard = getSystemService(ClipboardManager::class.java)
            ?: error("Clipboard unavailable")
        clipboard.setPrimaryClip(
            ClipData.newPlainText("MAGO diagnostics", summary),
        )
    }
}
```

The internal fixed error is caught and never displayed/logged.

- [ ] **Step 6: Thread typed model/callback through MagoApp and AppNavHost**

Add:

```kotlin
diagnosticsUiModel: DiagnosticsUiModel
onCopyDiagnosticsSummary: (String) -> Boolean
```

The destination must call only:

```kotlin
DiagnosticsScreen(
    uiModel = diagnosticsUiModel,
    onCopySummary = onCopyDiagnosticsSummary,
)
```

Add no `LaunchedEffect`, refresh callback, or coordinator action.

- [ ] **Step 7: Run app/feature tests, Build, and Lint**

```bash
gradle --no-daemon \
  :app:testDebugUnitTest \
  :feature:diagnostics:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/mago/android/DiagnosticsClipboard.kt \
  app/src/test/kotlin/dev/mago/android/DiagnosticsClipboardTest.kt \
  app/src/main/kotlin/dev/mago/android/MagoApp.kt \
  app/src/main/kotlin/dev/mago/android/MainActivity.kt
git commit -m "feat: wire masked diagnostics clipboard support"
```

---

### Task 4: CI, documentation, and final verification

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`

- [ ] **Step 1: Add focused tests to Android CI**

Add to the existing Gradle command:

```text
:app:testDebugUnitTest
:feature:diagnostics:testDebugUnitTest
```

Keep all current Bridge checks, Build, Lint, tests, and Debug APK upload unchanged.

- [ ] **Step 2: Update README**

Document About fields, manual diagnostics copy, strict allowlisting, no automatic upload, and omission of brand/model/device identifiers. Add the two test tasks to the verification command and diagnostics checks to the real-device smoke-test list.

- [ ] **Step 3: Run complete Android verification**

```bash
gradle --no-daemon --stacktrace \
  :app:assembleDebug \
  :app:lintDebug \
  :app:testDebugUnitTest \
  :domain:installation:testDebugUnitTest \
  :core:security:testDebugUnitTest \
  :core:termux:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :core:rpc:testDebugUnitTest \
  :core:reporting:testDebugUnitTest \
  :feature:modules:testDebugUnitTest \
  :feature:dashboard:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:reports:testDebugUnitTest \
  :feature:diagnostics:testDebugUnitTest
```

- [ ] **Step 4: Run Bridge reproducibility gate**

```bash
python3 -m pip install --disable-pip-version-check check-jsonschema
bash termux-bridge/tests/contract_test.sh
bash -n termux-bridge/scripts/dispatch.sh \
  termux-bridge/scripts/lib/common.sh \
  termux-bridge/scripts/actions/*.sh
termux-bridge/packaging/build_bundle.sh
git diff --exit-code -- \
  termux-bridge/scripts \
  termux-bridge/packaging/build_bundle.sh \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt
```

- [ ] **Step 5: Review scope and prepare PR**

```bash
git diff --stat main...HEAD
git diff --check main...HEAD
git status --short
```

Expected scope: approved spec/plan, Diagnostics models/UI/tests, App clipboard/wiring/tests, Android workflow, and README only. No RPC, Bridge, database, permissions, navigation route, reporting, modules, or terminal behavior changes.

Commit:

```bash
git add .github/workflows/android.yml README.md
git commit -m "ci: verify privacy-safe diagnostics support"
```

The PR body must document the allowlist, deny fragments, localhost derivation, passive flow, manual clipboard boundary, no-upload guarantee, focused tests, and fresh CI evidence.