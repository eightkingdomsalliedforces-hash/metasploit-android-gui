# MAGO Phase 7C About and Diagnostics Design

## Goal

Upgrade the existing Diagnostics destination into a privacy-preserving About and support surface. The page must show stable build and runtime information, present the current installation state, and let the user manually copy a strictly whitelisted diagnostic summary without triggering RPC, Termux Bridge, background work, file creation, or network upload.

## Approved Product Decisions

- About information is integrated at the top of the existing Diagnostics page.
- The diagnostic summary does not contain device brand or model.
- The summary may contain the safe installation stage, last successful stage, failure category, and safe error code.
- The summary never contains device identifiers, credentials, tokens, full paths, raw exception text, raw Bridge output, stack traces, or `AppError.diagnosticData`.
- Copying is always an explicit user action. MAGO never uploads or sends diagnostics automatically.
- Phase 7C adds no new RPC method, Bridge action, endpoint, permission, retry loop, polling, analytics, or background job.

## Existing Context

The current screen accepts `List<DiagnosticEntry>` and masks entries marked `sensitive`, but it has no About information, typed presentation model, stable copy format, or clipboard action.

`BootstrapCoordinator` already publishes the required passive sources:

- `state: StateFlow<InstallationState>`
- `environment: StateFlow<TermuxEnvironment?>`
- `metasploitVersion: StateFlow<MetasploitVersion?>`
- `diagnostics: StateFlow<List<DiagnosticEntry>>`

Phase 7C consumes these existing flows only. Entering the page does not call `inspectEnvironment()`, retry installation, run a Bridge command, or contact localhost RPC.

## Architecture

### DiagnosticsUiModel

The Diagnostics feature receives one typed, already-presented model:

```kotlin
data class DiagnosticsUiModel(
    val about: DiagnosticsAboutInfo,
    val system: DiagnosticsSystemInfo,
    val installation: DiagnosticsInstallationInfo,
    val bridgeEntries: List<DiagnosticsEntryUiModel>,
    val copySummary: String,
)
```

Recommended supporting types:

```kotlin
data class DiagnosticsAboutInfo(
    val appVersionName: String,
    val appVersionCode: Long,
    val minimumApi: Int,
    val bridgeVersion: Int,
    val bridgeSha256: String,
)

data class DiagnosticsSystemInfo(
    val androidRelease: String,
    val apiLevel: Int,
    val primaryAbi: String,
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

Missing values are represented as `null` in typed models and rendered as `尚未取得` on screen or `unknown` in the copied summary. Absence of an error is rendered as `無` on screen and `none` in the copied summary.

### DiagnosticsPresenter

`DiagnosticsPresenter` combines platform/build values and the four existing coordinator flows into `DiagnosticsUiModel`.

Inputs:

- MAGO `BuildConfig.VERSION_NAME`
- MAGO `BuildConfig.VERSION_CODE`
- Android release and API level
- `Build.SUPPORTED_ABIS.firstOrNull()`
- minimum supported API 31
- `BridgeBundleMetadata.VERSION`
- `BridgeBundleMetadata.SHA256`
- `InstallationState`
- optional `MetasploitVersion`
- existing `DiagnosticEntry` values

The presenter does not access the clipboard, perform I/O, launch coroutines, or call repositories.

Device brand, model, serial, Android ID, advertising ID, installer identity, and other device-identifying fields are not accepted as presenter inputs.

### DiagnosticsSummaryBuilder

`DiagnosticsSummaryBuilder` is a pure formatter that produces the stable copied text. It accepts only typed data and exact whitelisted Bridge entries. It never serializes the complete `DiagnosticEntry` list.

The copied Bridge key order is fixed:

```kotlin
private val copiedBridgeKeys = listOf(
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

Exact matching is required. Unknown keys do not enter the summary.

Additional deny rules apply even if a future source incorrectly marks an entry as non-sensitive. A key is never copied when its normalized name contains any of:

- `password`
- `token`
- `credential`
- `secret`
- `path`
- `prefix`
- `serial`
- `deviceid`
- `androidid`

Entries marked `sensitive=true` are also never copied.

`bridge.rpcHost` is never copied verbatim. It is converted to one of:

- `RPC localhost: true`
- `RPC localhost: false`
- `RPC localhost: unknown`

Only `127.0.0.1`, `::1`, and `localhost` count as localhost.

### Clipboard Boundary

`DiagnosticsScreen` exposes one callback:

```kotlin
onCopySummary: (String) -> Unit
```

The App layer writes the supplied text to `ClipboardManager` with label `MAGO diagnostics`.

The screen and summary builder never access Android clipboard APIs directly. Clipboard failures are caught at the App layer and converted into a fixed success or failure UI event. Exception messages are not shown or added to diagnostics.

## Screen Design

The destination uses one `LazyColumn` to avoid nested vertical scrolling on phones, tablets, landscape layouts, and large font scales.

### 1. About MAGO Card

Displays:

- `MAGO <versionName> (<versionCode>)`
- Bridge bundle version
- first 12 hexadecimal characters of the Bridge SHA-256 followed by an ellipsis
- `Android 12 / API 31 以上`
- `診斷資料不會自動上傳`

The complete Bridge SHA-256 is included only in the copied summary.

No update check, website link, release download, or clickable SHA is added.

### 2. System and Installation Card

Displays:

- Android release and API level
- primary CPU ABI
- current installation stage
- last successful stage
- failure kind
- safe error code

It does not show `AppError.userMessage`, `diagnosticData`, operation IDs, retry counters, stack traces, raw stdout/stderr, or arbitrary environment strings.

### 3. Bridge Status Section

Existing diagnostic entries remain visible, but rendering follows a fail-closed rule:

- exact approved display keys and non-sensitive values are shown
- sensitive or unknown entries display `已隱藏`

The display allowlist may include the same Bridge health keys used by the copied summary. Unknown future entries must not become visible by default.

### 4. Copy Action

A full-width button reads:

`複製已遮罩的診斷摘要`

After a successful clipboard write, show `已複製診斷摘要` in the screen or a Snackbar. On failure, show `無法複製診斷摘要`.

The summary is not rendered inside an editable text field and is not saved to a file.

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
BuildConfig / Build / BridgeBundleMetadata
                    │
BootstrapCoordinator flows
                    │
                    ▼
          DiagnosticsPresenter
                    │
                    ▼
          DiagnosticsUiModel
             │             │
             │             └── DiagnosticsSummaryBuilder
             ▼                              │
      DiagnosticsScreen                    ▼
             │                       copySummary text
             └──────── explicit callback ────────┐
                                                 ▼
                                        App clipboard layer
```

No source read is initiated by the screen.

## Error Handling

- Missing platform or coordinator values render as `尚未取得` and copy as `unknown`.
- Duplicate diagnostic keys are normalized into a map; the last received value wins before allowlist filtering.
- Unknown diagnostic keys remain hidden and are omitted from copied text.
- Clipboard service absence or runtime failure produces the fixed failure message without retry.
- Presenter or summary formatting must be total functions and must not throw on missing data.
- No failure path writes diagnostics to Logcat, files, analytics, exception text, or network services.

## Accessibility and Responsive Behavior

- The page uses one `LazyColumn` and visible text labels.
- The copy control is a full-width text button.
- Status feedback is visible in screen semantics or a Snackbar, not Toast-only feedback.
- The implementation uses existing MAGO theme, dark mode, font scaling, and reduced-motion settings.
- No animation is introduced.

## Testing

Focused unit tests cover:

1. Summary includes App, Android, ABI, Bridge, Metasploit, and installation status.
2. Summary contains no brand, model, serial, Android ID, or other device identifier.
3. Sensitive entries are excluded.
4. Unknown Bridge keys are excluded.
5. Keys containing password, token, credential, secret, path, prefix, serial, device ID, or Android ID are excluded even when `sensitive=false`.
6. `rpcHost=127.0.0.1`, `::1`, or `localhost` produces `RPC localhost: true`.
7. A non-localhost RPC host produces `false` without exposing the address.
8. Safe error code, current stage, last successful stage, and failure kind are included.
9. `userMessage`, `diagnosticData`, stack traces, paths, and raw error bodies are not represented in the model or summary.
10. Duplicate keys resolve deterministically and output order is fixed.
11. Missing data produces `unknown` or `none` without throwing.
12. Presenter construction does not call coordinator actions, repositories, Bridge commands, or RPC.

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
