# Settings and Maintenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add explicit, user-triggered Metasploit update and cache-cleaning controls to the existing dashboard without introducing background work or arbitrary commands.

**Architecture:** `DashboardViewModel` reuses the existing `TermuxGateway` allowlisted Bridge actions. A confirmation state prevents execution until the user explicitly confirms. Successful maintenance actions are followed by one `HEALTH_CHECK`; failures stop immediately and are shown to the user.

**Tech Stack:** Kotlin 2.4, StateFlow, Compose Material 3, existing Termux Bridge v2, JUnit/Truth/coroutines-test.

## Global Constraints

- RPC and Bridge remain localhost/allowlist only.
- No automatic update, background scheduling, silent retry, arbitrary Shell, or user-supplied command.
- Only `UPDATE_METASPLOIT`, `CLEAN_CACHE`, and post-success `HEALTH_CHECK` are callable from this slice.
- One maintenance action at a time.
- Every mutating action requires a fresh confirmation; confirmation is never remembered.
- Tests remain risk-based: confirmation gate, fail-closed readiness, action ordering, and failure short-circuit only.

---

### Task 1: Add maintenance state and confirmation gate

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
- Test: `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `TermuxGateway.execute(BridgeAction, operationId)` and `BootstrapCoordinator.state`.
- Produces: `MaintenanceAction`, confirmation state, `requestMaintenance`, `confirmMaintenance`, and `cancelMaintenance`.

- [ ] Add a failing test proving request-only performs zero Bridge calls.
- [ ] Add a failing test proving confirmation runs exactly the selected action followed by `HEALTH_CHECK`.
- [ ] Add a failing test proving non-READY installation performs zero Bridge calls.
- [ ] Implement the minimal state machine and operation ordering.
- [ ] Run `:feature:dashboard:testDebugUnitTest`.

### Task 2: Add dashboard maintenance controls

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: callbacks and state from `DashboardUiState`.
- Produces: explicit update/clean buttons, confirmation dialog, progress, success, and error copy.

- [ ] Add a maintenance section below service status cards.
- [ ] Add an `AlertDialog` with action-specific warning text.
- [ ] Disable all maintenance controls while one action is running.
- [ ] Do not add screenshot or Compose UI tests; validate with Build and Lint.

### Task 3: Wire the existing gateway and verify CI

**Files:**
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Interfaces:**
- Consumes: `AppContainer.termuxGateway`.
- Produces: `DashboardViewModel.factory(coordinator, operationsRepository, termuxGateway)`.

- [ ] Pass the existing allowlisted gateway into the dashboard ViewModel.
- [ ] Run GitHub Actions Build, Lint, Bridge validation, and risk-based tests.
- [ ] Download and verify the debug APK Artifact.
- [ ] Merge only after green CI.
