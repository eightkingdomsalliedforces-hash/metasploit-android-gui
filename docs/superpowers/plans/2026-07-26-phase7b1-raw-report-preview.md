# MAGO Phase 7B.1 Raw Report Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the previously approved in-app full raw report preview while keeping JSON, CSV, HTML, and ZIP export on the existing sanitized whitelist.

**Architecture:** `ReportsViewModel` continues to keep the source `ReportPreviewSnapshot` in memory and exports only through `toSafeReportSnapshot()`. `ReportPreviewSnapshot.toUiModel()` changes from sanitized mapping to a bounded display-only mapping that includes all known source fields and recursively rendered `extraFields`; the Compose screen renders this display model and never writes it to storage, logs, diagnostics, or export builders.

**Tech Stack:** Kotlin, Android ViewModel, Jetpack Compose Material 3, JUnit 4, Truth, GitHub Actions, Gradle 9.5.0, JDK 17, Android SDK 36.

## Global Constraints

- Start from `main` commit `67557e56bed47f53f7d78bc86b3116149be1019c`.
- Android minimum API 31; compile SDK 36; Build Tools 36.0.0; JDK 17.
- Keep localhost-only repositories and the 100-record limit for Hosts, Services, Vulnerabilities, and execution history.
- Add no RPC method, endpoint, permission, persistence, analytics, logging, background polling, pagination, or automatic retry.
- Do not add Credentials or Loot reads.
- Raw preview content exists only in the ViewModel/UI process memory.
- JSON, CSV, HTML, and ZIP continue to use `toSafeReportSnapshot()` and the existing SAF writer.
- UI rendering limits: maximum nesting depth 8; maximum 500 map or array items per container; maximum 65,536 characters per string; maximum 4,096 binary bytes rendered as hexadecimal.
- Rendering limits may truncate only the public display model; they must not mutate the private source snapshot.

---

## File Map

- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewPresentation.kt`
- Create `feature/reports/src/test/kotlin/dev/mago/android/reports/RawReportPreviewPresentationTest.kt`
- Modify `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`
- Modify `README.md`

---

### Task 1: Define bounded full-preview presentation data

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportPreviewPresentation.kt`
- Create: `feature/reports/src/test/kotlin/dev/mago/android/reports/RawReportPreviewPresentationTest.kt`

**Produces:**

```kotlin
data class ReportPreviewField(
    val label: String,
    val value: ReportPreviewValue,
)

sealed interface ReportPreviewValue {
    data class Scalar(val text: String, val truncated: Boolean = false) : ReportPreviewValue
    data class Binary(val hex: String, val totalBytes: Int, val truncated: Boolean) : ReportPreviewValue
    data class Array(val values: List<ReportPreviewValue>, val truncated: Boolean) : ReportPreviewValue
    data class Object(val entries: List<ReportPreviewObjectEntry>, val truncated: Boolean) : ReportPreviewValue
}

data class ReportPreviewObjectEntry(
    val key: String,
    val value: ReportPreviewValue,
)
```

Every record item keeps its existing identity fields and adds `fields: List<ReportPreviewField>`. The Workspace preview adds ID, created time, updated time, and `extraFields`.

- [ ] Write a failing test that creates one Workspace, Host, Service, Vulnerability, and execution record containing sentinel MAC, info, comments, service pack, language, timestamps, resource, result summary, error, and nested `extraFields`; assert all sentinel values are represented by `toUiModel()`.
- [ ] Write failing tests for deterministic map-key sorting, depth 8 truncation, 500-element truncation, 65,536-character string truncation, and 4,096-byte binary truncation.
- [ ] Run `gradle --no-daemon :feature:reports:testDebugUnitTest --tests 'dev.mago.android.reports.RawReportPreviewPresentationTest'` and confirm RED because the full-preview field/value types do not exist.
- [ ] Implement the smallest immutable display model and recursive `RpcValue` conversion satisfying the limits. Do not call `toSafeReportSnapshot()` inside `toUiModel()`.
- [ ] Keep execution option values exactly as already stored in `redactedOptions`; include locally stored `resultSummary` and `error` only in the in-app display model.
- [ ] Run `gradle --no-daemon :core:reporting:testDebugUnitTest :feature:reports:testDebugUnitTest` and confirm GREEN. Existing report-secret exclusion tests must remain unchanged and pass.

---

### Task 2: Render complete known fields and recursive extra fields

**Files:**
- Modify: `feature/reports/src/main/kotlin/dev/mago/android/reports/ReportsScreen.kt`

- [ ] Update the warning card to state that the in-app preview can contain asset notes, service information, local execution result/error text, and unknown RPC fields, but safe exports exclude them.
- [ ] On phones, make each record card expandable and render every `ReportPreviewField` only after expansion.
- [ ] Recursively render scalar, binary, array, and object values with indentation and explicit truncation labels.
- [ ] Preserve the existing single `LazyColumn`, category chips, refresh states, format selector, export action, SAF behavior, large-font horizontal scrolling, App lock, theme, and reduced-motion behavior.
- [ ] Ensure the export button text remains `產生安全版報告並選擇儲存位置`.
- [ ] Run `gradle --no-daemon :feature:reports:testDebugUnitTest :app:assembleDebug :app:lintDebug` and confirm success.

---

### Task 3: Update repository documentation

**Files:**
- Modify: `README.md`

- [ ] Replace `Phase 2 現況` with a current capability matrix covering installation, controlled module operations, local history/favorites, read-only Jobs/Sessions summary, inventory and Workspace management, raw in-app preview versus sanitized export, App lock, secure screen, display/accessibility settings, Debug CI, and manual unsigned Release artifacts.
- [ ] Clearly distinguish Debug APK artifacts from unsigned Release APK/AAB artifacts and state that unsigned outputs are not production-signed releases.
- [ ] Update verification commands to include `core:reporting`, `feature:modules`, `feature:dashboard`, `feature:inventory`, and `feature:reports` tests.
- [ ] Add a manual Android smoke-test checklist for Termux authorization, Bridge recovery, RPC lifecycle, Workspace/inventory, module confirmation, App lock, 100%–200% font scale, preview refresh failure preservation, and four SAF report formats.

---

### Task 4: Verification and obsolete PR cleanup

- [ ] Run the complete Android CI command configured in `.github/workflows/android.yml`.
- [ ] Run Termux Bridge contract, shell syntax, bundle rebuild, and embedded bundle diff checks.
- [ ] Confirm the branch changes only the plan, two report source/test files, and README.
- [ ] Open a ready-for-review PR with fresh CI evidence.
- [ ] After the replacement PR is verified, add replacement comments to PR #26 and PR #12 and close both as obsolete/not planned. Do not merge either branch.