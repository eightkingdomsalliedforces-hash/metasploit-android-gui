# MAGO Phase 7C About and Diagnostics Design

## Goal

Upgrade the existing Diagnostics destination into a privacy-preserving About and support surface. The page shows stable build and runtime information, presents the current installation state, and lets the user manually copy a strictly whitelisted diagnostic summary without triggering RPC, Termux Bridge, background work, file creation, or network upload.

## Approved Product Decisions

- About information is integrated at the top of the existing Diagnostics page.
- The diagnostic summary does not contain device brand or model.
- The summary contains the safe installation stage, last successful stage, failure category, and safe error code.
- The summary never contains device identifiers, credentials, tokens, full paths, raw exception text, raw Bridge output, stack traces, or `AppError.diagnosticData`.
- Copying is always an explicit user action. MAGO never uploads or sends diagnostics automatically.
- Phase 7C adds no new RPC method, Bridge action, endpoint, permission, retry loop, polling, analytics, or background job.

## Existing Context

The current screen accepts `List<DiagnosticEntry>` and masks entries marked `sensitive`, but it has no About information, typed presentation model, stable copy format, or clipboard action.

`BootstrapCoordinator` already publishes the passive sources required by this feature:

- `state: StateFlow<InstallationState>`
- `environment: StateFlow<TermuxEnvironment?>`
- `metasploitVersion: StateFlow<MetasploitVersion?>`
- `diagnostics: StateFlow<List<DiagnosticEntry>>`

Phase 7C consumes these existing values only. Entering the page does not call `inspectEnvironment()`, retry installation, run a Bridge command, or contact localhost RPC.

## Module and Component Boundaries

### App Layer: Platform Input and Clipboard

The App module is the only layer allowed to read App-specific and Android platform constants:

- `BuildConfig.VERSION_NAME`
- `BuildConfig.VERSION_CODE`
- `Build.VERSION.RELEASE`
- `Build.VERSION.SDK_INT`
- `Build.SUPPORTED_ABIS.firstOrNull()`
- minimum supported API 31
- `BridgeBundleMetadata.VERSION`
- `BridgeBundleMetadata.SHA256`

It maps these values and the current coordinator flows into a feature-owned primitive input model. The Diagnostics feature never depends on the App module or its `BuildConfig`.

The App layer also owns `ClipboardManager`. It exposes one synchronous callback to Compose:

```kotlin
onCopySummary: (String) -> Boolean
```

The callback writes the text with clipboard label `MAGO diagnostics`, catches platform failures, never logs the summary or exception, and returns `true` only when the write completes without throwing.

### Diagnostics Feature: Typed Presentation

The feature module owns the following required models:

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
```

Missing optional values remain `null` in the typed model. The screen renders them as `尚未取得`; the copied summary renders them as `unknown`. Absence of failure or error values renders as `無` on screen and `none` in the summary.

### DiagnosticsPresenter

`DiagnosticsPresenter` is a pure feature-layer component:

```kotlin
fun present(input: DiagnosticsPresentationInput): DiagnosticsUiModel
```

It performs no Android API access, I/O, coroutine launch, repository call, Bridge action, or RPC call.

Device brand, model, serial, Android ID, advertising ID, installer identity, and other device-identifying fields do not exist in `DiagnosticsPresentationInput`, so they cannot enter the presentation or summary paths accidentally.

### DiagnosticsSummaryBuilder

`DiagnosticsSummaryBuilder` is a pure formatter that produces stable copied text from typed values and exact whitelisted Bridge entries. It never serializes the complete `DiagnosticEntry` list.

## Exact Bridge Allowlists

The display and copy allowlists are identical and ordered:

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

Exact matching is required. Unknown future keys remain hidden and never enter copied text.

Additional deny rules apply even if a key is accidentally added to the allowlist or incorrectly marked non-sensitive. A key is omitted when its lowercase normalized name contains any of:

- `password`
- `token`
- `credential`
- `secret`
- `path`
- `prefix`
- `serial`
- `deviceid`
- `androidid`

Entries marked `sensitive=true` are also omitted from the display value and copied summary.

`bridge.rpcHost` is never displayed or copied verbatim. It is converted to one of:

- screen: `是`, `否`, or `尚未取得`
- summary: `RPC localhost: true`, `false`, or `unknown`

Only `127.0.0.1`, `::1`, and case-insensitive `localhost` count as localhost.

Duplicate diagnostic keys are reduced before filtering; the last received entry wins. Output order follows `allowedBridgeKeys`, not source-list order.

## Screen Design

The destination uses one `LazyColumn` to avoid nested vertical scrolling on phones, tablets, landscape layouts, and large font scales.

### 1. About MAGO Card

Displays:

- `MAGO <versionName> (<versionCode>)`
- Bridge bundle version
- first 12 hexadecimal characters of the Bridge SHA-256 followed by an ellipsis
- `Android 12 / API 31 以上`
- `診斷資料不會自動上傳`

The complete Bridge SHA-256 is included only in copied text. No update check, website link, release download, or clickable SHA is added.

### 2. System and Installation Card

Displays:

- Android release and API level
- primary CPU ABI
- Metasploit version
- current installation stage
- last successful stage
- failure kind
- safe error code

It does not show `AppError.userMessage`, `diagnosticData`, operation IDs, retry counters, stack traces, raw stdout/stderr, or arbitrary environment strings.

### 3. Bridge Status Section

The section renders exactly the ordered `allowedBridgeKeys` that survive the deny and sensitive checks.

- approved non-sensitive values are displayed
- `bridge.rpcHost` is replaced by the derived localhost status
- sensitive, denied, missing, and unknown values display `已隱藏` or `尚未取得` as appropriate
- arbitrary source entries are never appended to the list

### 4. Copy Action and Status

A full-width button reads:

`複製已遮罩的診斷摘要`

On click, `DiagnosticsScreen` passes `uiModel.copySummary` to `onCopySummary`. The screen stores only a transient local result enum:

```kotlin
enum class DiagnosticsCopyStatus { SUCCESS, FAILURE }
```

- `true` result displays `已複製診斷摘要`
- `false` result displays `無法複製診斷摘要`

The status is visible in screen semantics or a Snackbar. It is not Toast-only feedback. The summary itself is not kept in local UI state, rendered in an editable field, or saved to a file.

## Stable Summary Format

The copied text uses English field labels for reliable issue search and cross-language comparison:

```text
MAGO Diagnostics
App: 0.7.0 (7)
Android: 16 / API 36
ABI: arm64-v8a
Minimum API: 31
Bridge: v2
Bridge SHA-256: <full sha256>
Metasploit: <version or unknown>

Installation stage: READY
Last successful stage: VERIFYING
Failure kind: none
Error code: none

frameworkRepository: true
msfconsole: true
databaseInitialized: true
databaseConfig: true
databaseReady: true
rpcConfigured: true
rpcProcessRunning: true
rpcPortOpen: true
RPC localhost: true
rpcPort: 55552

Privacy:
- Device brand/model omitted
- Device identifiers omitted
- Credentials/tokens omitted
- Paths and raw errors omitted
- This report is copied manually and is never uploaded automatically
```

All fields are emitted in this fixed order. Missing optional values use `unknown`; absent failure and error values use `none`.

## Data Flow

```text
App BuildConfig / Android Build / BridgeBundleMetadata
BootstrapCoordinator current StateFlow values
                         │
                         ▼
      App constructs DiagnosticsPresentationInput
                         │
                         ▼
              DiagnosticsPresenter
                         │
                         ▼
               DiagnosticsUiModel
                    │          │
                    │          └── stable copySummary
                    ▼
             DiagnosticsScreen
                    │ explicit button click
                    ▼
          App clipboard callback -> Boolean
                    │
                    ▼
        fixed success or failure screen status
```

No source read is initiated by the screen or presenter.

## Error Handling

- Missing platform or coordinator values render as `尚未取得` and copy as `unknown`.
- Unknown diagnostic keys remain hidden and are omitted from copied text.
- Clipboard service absence or runtime failure returns `false` without retry.
- Clipboard exceptions and summary text are not logged.
- Presenter and summary builder are total functions and do not throw on missing data.
- No failure path writes diagnostics to files, analytics, exception text, or network services.

## Accessibility and Responsive Behavior

- The page uses one `LazyColumn` and visible text labels.
- The copy control is a full-width text button.
- Status feedback is visible in semantics or a Snackbar.
- The implementation uses existing MAGO theme, dark mode, font scaling, and reduced-motion settings.
- No animation is introduced.

## Testing

Focused unit tests cover:

1. Summary includes App, Android, ABI, Bridge, Metasploit, and installation status.
2. Presentation input has no brand, model, serial, Android ID, or other device identifier field.
3. Sensitive entries are excluded.
4. Unknown Bridge keys are excluded.
5. Keys containing password, token, credential, secret, path, prefix, serial, device ID, or Android ID are excluded even when `sensitive=false`.
6. `rpcHost=127.0.0.1`, `::1`, or `localhost` produces localhost `true`.
7. A non-localhost RPC host produces `false` without exposing the address.
8. Safe error code, current stage, last successful stage, and failure kind are included.
9. `userMessage`, `diagnosticData`, stack traces, paths, and raw error bodies are not represented in the input model or summary.
10. Duplicate keys resolve deterministically and output order is fixed.
11. Missing data produces `unknown` or `none` without throwing.
12. Presenter construction performs no coordinator action, repository call, Bridge command, or RPC.
13. Clipboard callback returns success and failure without logging or exposing exception text.

The verification gate reruns:

- `:app:assembleDebug`
- `:app:lintDebug`
- `:domain:installation:testDebugUnitTest`
- new Diagnostics feature tests
- all existing risk-directed tests configured by `.github/workflows/android.yml`
- Termux Bridge contract, shell syntax, checksum, and reproducible bundle checks

## Acceptance Criteria

Phase 7C is complete only when:

- the Diagnostics destination contains About, system, installation, and Bridge status sections
- the user can manually copy the stable masked summary
- the summary excludes device brand/model, identifiers, credentials, tokens, complete paths, raw errors, and unknown diagnostic keys
- safe error code and stage information are included
- entering the page triggers no RPC, Bridge, installation inspection, retry, upload, or background work
- clipboard failure is handled with fixed user-facing text
- large fonts, dark mode, and reduced-motion remain compatible
- the complete Android CI gate and Debug APK artifact succeed

## Out of Scope

- automatic support upload
- email/share intent
- diagnostic file export
- automatic environment refresh
- app update checking
- release download links
- remote telemetry or analytics
- device brand/model collection
- unsigned Release workflow changes
