# MAGO Phase 7C About and Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the Diagnostics destination into a privacy-preserving About and support surface with a stable, manually copied, strictly whitelisted diagnostic summary.

**Architecture:** The App module reads `BuildConfig`, Android `Build`, `BridgeBundleMetadata`, and the current `BootstrapCoordinator` flow values, then creates a primitive `DiagnosticsPresentationInput`. The Diagnostics feature owns a pure presenter and summary builder that apply exact allowlists and fail-closed deny rules; Compose renders the resulting `DiagnosticsUiModel`, while the App layer alone writes the summary to the system clipboard and reports a Boolean result.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android ClipboardManager, JUnit 4, Truth, Gradle 9.5.0, JDK 17, Android SDK 36.

## Global Constraints

- Start implementation from `main` commit `31b4a64234585090169157acda0895d983959072`.
- Android minimum API 31; compile SDK 36; Build Tools 36.0.0; JDK 17.
- About information remains inside the existing Diagnostics destination; add no navigation route.
- Do not collect or display device brand, model, serial, Android ID, advertising ID, installer identity, or another device identifier.
- Include safe installation stage, last successful stage, failure category, and `AppError.errorCode`; exclude `userMessage`, `diagnosticData`, stack traces, raw exception text, raw Bridge output, operation IDs, retry counters, and complete paths.
- Add no RPC method, Bridge action, endpoint, permission, persistence, analytics, automatic upload, background job, polling, automatic retry, share intent, email intent, file export, update check, or release download.
- Entering Diagnostics must not call `inspectEnvironment()`, retry installation, run a Bridge command, or contact localhost RPC.
- Clipboard copying is explicit user action only; label copied data `MAGO diagnostics`.
- Clipboard failures return fixed UI text and never expose or log exception messages or copied content.
- Display and copied Bridge values use the same exact ordered allowlist; unknown keys fail closed.
- Run only focused tests; do not add a broad screenshot or instrumentation suite.

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

### Task 1: Build the pure diagnostics presenter and stable summary

**Files:**
- Create: `feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentation.kt`
- Create: `feature/diagnostics/src/test/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentationTest.kt`
- Modify: `feature/diagnostics/build.gradle.kts`

**Interfaces:**

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

- [ ] **Step 1: Add test dependencies**

Add to `feature/diagnostics/build.gradle.kts`:

```kotlin
testImplementation(libs.junit)
testImplementation(libs.truth)
```

- [ ] **Step 2: Write the failing presenter and summary tests**

Create tests using sentinel secrets:

```kotlin
@Test
fun `summary includes stable support fields in fixed order`() {
    val model = DiagnosticsPresenter.present(
        input(
            diagnosticEntries = listOf(
                entry("bridge.rpcPort", "rpcPort", "55552"),
                entry("bridge.frameworkRepository", "frameworkRepository", "true"),
                entry("bridge.rpcHost", "rpcHost", "127.0.0.1"),
            ),
        ),
    )

    assertThat(model.copySummary).contains("App: 0.7.0 (7)")
    assertThat(model.copySummary).contains("Android: 16 / API 36")
    assertThat(model.copySummary).contains("ABI: arm64-v8a")
    assertThat(model.copySummary).contains("Bridge: v2")
    assertThat(model.copySummary).contains("Installation stage: READY")
    assertThat(model.copySummary).contains("Error code: RPC_UNAVAILABLE")
    assertThat(model.copySummary).contains("RPC localhost: true")
    assertThat(model.copySummary.indexOf("frameworkRepository"))
        .isLessThan(model.copySummary.indexOf("RPC localhost"))
}
```

Add separate tests that assert:

```kotlin
assertThat(summary).doesNotContain("Pixel")
assertThat(summary).doesNotContain("SERIAL_SECRET")
assertThat(summary).doesNotContain("ANDROID_ID_SECRET")
assertThat(summary).doesNotContain("PASSWORD_SECRET")
assertThat(summary).doesNotContain("TOKEN_SECRET")
assertThat(summary).doesNotContain("/data/data/secret")
assertThat(summary).doesNotContain("RAW_EXCEPTION_SECRET")
assertThat(summary).doesNotContain("198.51.100.8")
```

Also cover:

- exact allowlist matching rejects `bridge.unknownFutureKey`
- `sensitive=true` rejects an otherwise allowlisted value
- deny fragments reject password, token, credential, secret, path, prefix, serial, deviceid, and androidid
- duplicate keys keep the final source entry
- `rpcHost` values `127.0.0.1`, `::1`, and case-insensitive `localhost` produce true
- a remote host produces false without exposing its value
- missing optional values produce `unknown`; absent failure/error produce `none`

- [ ] **Step 3: Run focused tests and confirm RED**

```bash
gradle --no-daemon :feature:diagnostics:testDebugUnitTest \
  --tests 'dev.mago.android.diagnostics.DiagnosticsPresentationTest'
```

Expected: test compilation fails because the presentation types and presenter do not exist.

- [ ] **Step 4: Implement immutable presentation models and exact filtering**

Use this exact ordered allowlist:

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

Use this deny-fragment set after lowercasing and removing `_`, `-`, and `.`:

```kotlin
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
```

Reduce duplicate entries with last-value-wins before filtering. Never copy `rpcHost` verbatim; derive true only for `127.0.0.1`, `::1`, or `localhost` ignoring case.

Emit the summary in the exact order and wording from the approved design, ending with the five Privacy lines.

- [ ] **Step 5: Run focused tests and confirm GREEN**

```bash
gradle --no-daemon :feature:diagnostics:testDebugUnitTest
```

Expected: all Diagnostics presentation tests pass.

- [ ] **Step 6: Commit Task 1**

```bash
git add \
  feature/diagnostics/build.gradle.kts \
  feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentation.kt \
  feature/diagnostics/src/test/kotlin/dev/mago/android/diagnostics/DiagnosticsPresentationTest.kt
git commit -m "feat: add privacy-safe diagnostics presentation"
```

---

### Task 2: Replace the Diagnostics screen with the complete About and support UI

**Files:**
- Modify: `feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsScreen.kt`

**Interfaces:**

```kotlin
@Composable
fun DiagnosticsScreen(
    uiModel: DiagnosticsUiModel,
    onCopySummary: (String) -> Boolean,
)
```

- [ ] **Step 1: Replace the root layout with one LazyColumn**

Use `Modifier.fillMaxSize().padding(16.dp)` and `Arrangement.spacedBy(12.dp)`. Add title `診斷資訊` and description `診斷資料只會在你按下複製後寫入系統剪貼簿，不會自動上傳。`.

- [ ] **Step 2: Add the About card**

Render:

```text
MAGO <versionName> (<versionCode>)
Bridge bundle：v<bridgeVersion>
Bridge SHA-256：<first 12 chars>…
最低支援：Android 12 / API 31
```

Handle a short SHA safely with `take(12)`; never throw.

- [ ] **Step 3: Add system and installation cards**

Render Android release/API, primary ABI, Metasploit version, current stage, last successful stage, failure kind, and safe error code. Use `尚未取得` for missing optional platform values and `無` for absent failure/error.

- [ ] **Step 4: Add the exact ordered Bridge status section**

Render only `uiModel.bridgeEntries`. Do not render the original `DiagnosticEntry` list. Values already arrive masked or derived from the presenter.

- [ ] **Step 5: Add manual copy and transient status**

Use:

```kotlin
var copyStatus by remember { mutableStateOf<DiagnosticsCopyStatus?>(null) }
```

On click:

```kotlin
copyStatus = if (onCopySummary(uiModel.copySummary)) {
    DiagnosticsCopyStatus.SUCCESS
} else {
    DiagnosticsCopyStatus.FAILURE
}
```

Render `已複製診斷摘要` or `無法複製診斷摘要` below the full-width button. Do not store the copied summary in local state and do not use Toast-only feedback.

- [ ] **Step 6: Compile the feature screen**

```bash
gradle --no-daemon :feature:diagnostics:compileDebugKotlin
```

Expected: compilation succeeds.

- [ ] **Step 7: Commit Task 2**

```bash
git add feature/diagnostics/src/main/kotlin/dev/mago/android/diagnostics/DiagnosticsScreen.kt
git commit -m "feat: add About and diagnostic support screen"
```

---

### Task 3: Wire passive platform data and safe clipboard handling through the App layer

**Files:**
- Create: `app/src/main/kotlin/dev/mago/android/DiagnosticsClipboard.kt`
- Create: `app/src/test/kotlin/dev/mago/android/DiagnosticsClipboardTest.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Interfaces:**

```kotlin
internal fun tryWriteDiagnosticsClipboard(write: () -> Unit): Boolean
```

Add to `MagoApp` and `AppNavHost`:

```kotlin
diagnosticsUiModel: DiagnosticsUiModel
onCopyDiagnosticsSummary: (String) -> Boolean
```

- [ ] **Step 1: Write the failing clipboard-boundary test**

```kotlin
@Test
fun `clipboard helper returns true when writer succeeds`() {
    assertThat(tryWriteDiagnosticsClipboard {}).isTrue()
}

@Test
fun `clipboard helper returns false and suppresses exception`() {
    assertThat(
        tryWriteDiagnosticsClipboard { error("RAW_CLIPBOARD_EXCEPTION") },
    ).isFalse()
}
```

- [ ] **Step 2: Run the app test and confirm RED**

```bash
gradle --no-daemon :app:testDebugUnitTest \
  --tests 'dev.mago.android.DiagnosticsClipboardTest'
```

Expected: compilation fails because `tryWriteDiagnosticsClipboard` does not exist.

- [ ] **Step 3: Implement the minimal clipboard boundary**

```kotlin
internal fun tryWriteDiagnosticsClipboard(write: () -> Unit): Boolean =
    try {
        write()
        true
    } catch (_: Exception) {
        false
    }
```

Do not log the exception or copied content.

- [ ] **Step 4: Build DiagnosticsPresentationInput from existing passive values**

In `UnlockedMagoContent`, collect the existing coordinator values:

```kotlin
val installationState by onboardingViewModel.state.collectAsStateWithLifecycle()
val environment by container.bootstrapCoordinator.environment.collectAsStateWithLifecycle()
val metasploitVersion by container.bootstrapCoordinator.metasploitVersion.collectAsStateWithLifecycle()
val diagnostics by container.bootstrapCoordinator.diagnostics.collectAsStateWithLifecycle()
```

The `environment` value may remain unused in presentation; collecting it is unnecessary unless the final compile proves an approved field needs it. Do not add brand/model fields.

Construct input with:

```kotlin
DiagnosticsPresentationInput(
    appVersionName = BuildConfig.VERSION_NAME,
    appVersionCode = BuildConfig.VERSION_CODE.toLong(),
    minimumApi = 31,
    bridgeVersion = BridgeBundleMetadata.VERSION,
    bridgeSha256 = BridgeBundleMetadata.SHA256,
    androidRelease = Build.VERSION.RELEASE,
    apiLevel = Build.VERSION.SDK_INT,
    primaryAbi = Build.SUPPORTED_ABIS.firstOrNull(),
    metasploitVersion = metasploitVersion?.toString(),
    currentStage = installationState.stage.name,
    lastSuccessfulStage = installationState.lastSuccessfulStage?.name,
    failureKind = installationState.failureKind?.name,
    errorCode = installationState.lastError?.errorCode,
    diagnosticEntries = diagnostics,
)
```

Before committing, inspect the actual `MetasploitVersion` model. Replace `toString()` with its explicit version property when available; never expose unrelated fields.

- [ ] **Step 5: Add clipboard callback**

Use `getSystemService(ClipboardManager::class.java)` and:

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

The fixed error string remains internal and is caught; it is never displayed or logged.

- [ ] **Step 6: Replace raw DiagnosticsScreen wiring**

Pass `diagnosticsUiModel` and `onCopyDiagnosticsSummary` through `MagoApp` and `AppNavHost`. The Diagnostics destination must call only:

```kotlin
DiagnosticsScreen(
    uiModel = diagnosticsUiModel,
    onCopySummary = onCopyDiagnosticsSummary,
)
```

Do not add `LaunchedEffect` or source refresh callbacks.

- [ ] **Step 7: Run app and feature tests, Build, and Lint**

```bash
gradle --no-daemon \
  :app:testDebugUnitTest \
  :feature:diagnostics:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

Expected: all commands succeed.

- [ ] **Step 8: Commit Task 3**

```bash
git add \
  app/src/main/kotlin/dev/mago/android/DiagnosticsClipboard.kt \
  app/src/test/kotlin/dev/mago/android/DiagnosticsClipboardTest.kt \
  app/src/main/kotlin/dev/mago/android/MagoApp.kt \
  app/src/main/kotlin/dev/mago/android/MainActivity.kt
git commit -m "feat: wire masked diagnostics clipboard support"
```

---

### Task 4: Put Diagnostics tests in the CI gate and document the support boundary

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`

- [ ] **Step 1: Extend the Android workflow test command**

Add these tasks to the existing Gradle invocation:

```text
:app:testDebugUnitTest
:feature:diagnostics:testDebugUnitTest
```

Keep Debug APK upload and all existing Bridge, Build, Lint, and risk-directed tests unchanged.

- [ ] **Step 2: Update README capabilities and verification command**

Add under App safety/display:

- About information and Bridge bundle identity
- manually copied, strictly whitelisted diagnostics
- no automatic diagnostics upload
- no brand/model/device identifier collection

Add `:app:testDebugUnitTest` and `:feature:diagnostics:testDebugUnitTest` to the documented Gradle verification command.

Add real-device smoke checks for:

- About fields and SHA short form
- sensitive/unknown Bridge entries hidden
- localhost status derived without showing host
- clipboard success message
- clipboard failure fixed message when simulated
- copied text contains no paths, raw error body, credentials, tokens, brand, model, or identifiers

- [ ] **Step 3: Run the complete Android verification gate**

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

- [ ] **Step 4: Run Bridge contract and bundle reproducibility checks**

```bash
python3 -m pip install --disable-pip-version-check check-jsonschema
bash termux-bridge/tests/contract_test.sh
bash -n \
  termux-bridge/scripts/dispatch.sh \
  termux-bridge/scripts/lib/common.sh \
  termux-bridge/scripts/actions/*.sh
termux-bridge/packaging/build_bundle.sh
git diff --exit-code -- \
  termux-bridge/scripts \
  termux-bridge/packaging/build_bundle.sh \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt
```

- [ ] **Step 5: Review scope**

```bash
git diff --stat main...HEAD
git diff --check main...HEAD
git status --short
```

Expected: only the approved design/plan, Diagnostics presentation/UI/tests, App wiring/clipboard tests, workflow, and README changed; no RPC, Bridge, database, permissions, navigation route, reporting, modules, or terminal behavior changed.

- [ ] **Step 6: Commit Task 4 and prepare PR**

```bash
git add .github/workflows/android.yml README.md
git commit -m "ci: verify privacy-safe diagnostics support"
```

The PR must describe the exact allowlist, deny fragments, localhost derivation, passive data flow, clipboard-only manual action, no-auto-upload boundary, focused tests, and fresh CI evidence.