# MAGO Phase 7B Full Report Preview UI Design

## Goal

Turn the Reports destination into a complete, readable preview of the exact safe data that will enter JSON, CSV, HTML, or ZIP export, while preserving the atomic snapshot and bounded-read guarantees added in Phase 7A.

## Approved Constraints

- Build from the latest `main`, after Phase 7A.
- Android minimum API 31; compile SDK 36; Build Tools 36.0.0; JDK 17.
- Keep the existing localhost-only repositories and the 100-record limit for Hosts, Services, Vulnerabilities, and execution history.
- Add no Credentials, Loot, Session automation, polling, pagination, automatic retry, new RPC method, new endpoint, or broad storage permission.
- Keep JSON, CSV, HTML, and ZIP SAF launchers, MIME types, and writer behavior unchanged.
- Keep preview data in memory only. Do not persist it to Room, DataStore, SavedStateHandle, logs, analytics, exceptions, or CI artifacts.
- Produce only Debug APK artifacts for this phase.
- Add only focused tests that protect state transitions and the preview safety boundary.

## Architecture

`ReportsViewModel` keeps the raw `ReportPreviewSnapshot` private because it is needed for export sanitization. Public `ReportsUiState` exposes only `ReportPreviewUiModel`, a presentation model derived from `toSafeReportSnapshot()`. This prevents Compose from accidentally rendering fields excluded from reports, such as Host MAC, `info`, `comments`, `extraFields`, vulnerability resource data, module result summaries, or raw errors.

`ReportPreviewPresentation.kt` owns the UI model and conversion. `ReportsScreen.kt` owns rendering only. Preview category selection is explicit ViewModel state so rotation and recomposition retain the selected category while the ViewModel lives.

## Public UI Model

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
```

The item models contain only exported fields:

- Host: address, name, state, operating system, operating-system flavor, purpose.
- Service: host, port, protocol, state, service name.
- Vulnerability: host, optional port and protocol, name, references.
- Execution: correlation ID, action, module type and name, status, optional job ID and UUID, redacted options, created and updated timestamps.

## Screen Layout

The destination uses one `LazyColumn` so it works on phones, tablets, large font scales, and landscape layouts without nested vertical scrolling.

1. Header row
   - Title: `報告預覽`
   - Short explanation that the preview matches the safe export.
   - `重新整理` outlined button.

2. Snapshot summary card
   - Active Workspace.
   - Snapshot generation time in the device locale and time zone.
   - Four compact count labels for Hosts, Services, Vulnerabilities, and execution history.
   - Notice that each category is capped at 100 records.

3. Safety notice
   - Clearly states that secrets, credentials, asset free text, raw module results, raw errors, Console content, and full paths are not shown or exported.

4. Format selector
   - Existing JSON, CSV, HTML, and ZIP chips.
   - Disabled only while export generation is running.

5. Preview category selector
   - Horizontally scrollable chips for the four categories, avoiding clipped fixed-width tabs at 160–200% font scale.
   - Every chip includes its item count.

6. Category list
   - Stable keys for all list items.
   - Full-width Material cards.
   - Only non-empty optional fields are rendered.
   - Redacted execution options are shown as key/value rows; values remain the already-redacted values supplied by the local store.

7. Export action
   - Full-width button: `產生安全版報告並選擇儲存位置`.
   - Disabled until a complete preview exists or while loading, refreshing, or exporting.

## Loading, Refresh, Error, and Empty States

- Initial load: show a progress indicator and `正在建立報告預覽…`; no export action is enabled.
- Refresh with an existing preview: keep the old preview visible, show progress, and disable refresh/export until completion.
- Refresh failure: keep the old preview and show the refresh error near the summary.
- Initial failure: show the error and keep the refresh button available for an explicit retry.
- Empty selected category: show a category-specific empty message instead of a blank region.
- Export and save messages stay separate from refresh errors.

## Accessibility and Responsive Behavior

- All controls have visible text labels; decorative icons are unnecessary.
- Category chips remain horizontally scrollable at large font sizes.
- Card content is plain semantic text in reading order.
- Progress indicators are accompanied by text.
- No animation is introduced, so the existing reduced-motion preference remains respected automatically.
- The screen keeps the current theme and font-scale composition locals provided by `MagoApp`.

## Data Flow

1. Enter Reports destination.
2. `ensurePreviewLoaded()` reads the five bounded sources once.
3. On complete success, ViewModel stores the raw snapshot privately and publishes `snapshot.toUiModel()` atomically.
4. User selects a category; only `selectedPreviewTab` changes and no source is read.
5. User refreshes; the old private/raw and public/safe snapshots remain active until a complete new snapshot succeeds.
6. User exports; the private raw snapshot is passed through `toSafeReportSnapshot()` and then to `ReportDocumentBuilder`, with zero extra source reads.

## Testing

Focused unit tests cover:

- Presentation conversion contains the permitted fields and has no representation for excluded fields.
- Initial load publishes the safe UI model.
- Selecting a preview category changes only UI state and performs zero repository/store reads.
- Existing atomic load, refresh preservation, duplicate-operation, zero-extra-read export, save-event, Build, Lint, Bridge, and report-format tests remain green.

No broad Compose screenshot or instrumentation suite is added in Phase 7B.