# MAGO Phase 3 Module Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a controlled graphical module workflow that loads options and compatible payloads, validates user input, performs explicit check or execute RPC calls, and tracks the returned UUID without autonomous target selection or retries.

**Architecture:** Extend the existing structured MessagePack RPC layer instead of parsing console text. The domain repository exposes typed module operations; `ModulesViewModel` owns form state and explicit confirmation state; Compose renders fields and results. Execution remains user initiated, localhost RPC only, never retries `module.check` or `module.execute`, and never generates targets automatically.

**Tech Stack:** Kotlin, Coroutines/Flow, Jetpack Compose Material 3, MessagePack RPC, JUnit/Truth, GitHub Actions.

## Global Constraints

- Android minimum remains API 31; compileSdk and targetSdk remain unchanged.
- RPC remains fixed to `http://127.0.0.1:55552/api`.
- Do not pass credentials or module values through shell commands.
- `module.check` and `module.execute` are never retried automatically.
- Execution requires an explicit confirmation step showing module, action and non-sensitive option summary.
- Sensitive option values are masked in UI summaries and excluded from diagnostics.
- No public-target discovery, bulk orchestration, AI exploit selection or autonomous chains.
- Tests remain risk-directed: RPC parsing, validation and state transitions only; no screenshot-test expansion.

---

### Task 1: Typed module operation RPC

**Files:**
- Modify: `core/model/src/main/kotlin/dev/mago/android/model/MetasploitModule.kt`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcModuleService.kt`
- Modify: `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitModuleRepository.kt`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitModuleRepositoryImpl.kt`
- Test: `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcModuleServiceTest.kt`

**Interfaces:**
- Produces: `compatiblePayloads(type, name)`, `check(request)`, `execute(request)`, `result(uuid)`.
- Produces models: `MetasploitModuleRequest`, `MetasploitModuleLaunch`, `MetasploitModuleRunStatus`, `MetasploitModuleRunResult`.

- [ ] Add RPC methods `module.options`, `module.compatible_payloads`, `module.check`, `module.execute`, `module.results`.
- [ ] Add immutable request/result models; options use `Map<String, String>` and are converted to an RPC map only inside `RpcModuleService`.
- [ ] Parse `job_id` as nullable integer and `uuid` as required non-blank text.
- [ ] Parse result states `ready`, `running`, `completed`, `errored`; preserve unknown result payload as `RpcValue`.
- [ ] Add focused service tests for request argument order, launch parsing and result-state parsing.
- [ ] Run `:core:rpc:testDebugUnitTest` and commit.

### Task 2: Form validation and safety boundary

**Files:**
- Create: `feature/modules/src/main/kotlin/dev/mago/android/modules/ModuleRunValidator.kt`
- Test: `feature/modules/src/test/kotlin/dev/mago/android/modules/ModuleRunValidatorTest.kt`
- Modify: `feature/modules/build.gradle.kts`

**Interfaces:**
- Produces: `ModuleRunValidation(errors: Map<String, String>, normalized: Map<String, String>)`.
- Consumes existing `MetasploitModuleOption` metadata.

- [ ] Validate required options after trimming.
- [ ] Validate integer, boolean and enum fields without rejecting normal Metasploit string values.
- [ ] Reject control characters and values larger than 16 KiB per field.
- [ ] Treat names containing `PASS`, `PASSWORD`, `TOKEN`, `KEY`, `SECRET` as sensitive for summaries.
- [ ] Build a redacted confirmation summary without changing the actual request values.
- [ ] Add focused validator tests and commit.

### Task 3: Module run state machine

**Files:**
- Modify: `feature/modules/src/main/kotlin/dev/mago/android/modules/ModulesViewModel.kt`
- Test: `feature/modules/src/test/kotlin/dev/mago/android/modules/ModulesViewModelTest.kt`

**Interfaces:**
- Adds UI state: editable values, compatible payloads, validation errors, confirmation request, launch receipt and result state.
- Adds actions: `setOption`, `requestCheck`, `requestExecute`, `confirmRun`, `cancelRun`, `refreshResult`.

- [ ] Populate defaults whenever module info loads.
- [ ] Load compatible payloads only for exploit/evasion modules; do not fail module details when this optional call fails.
- [ ] Build a request only after validator success.
- [ ] Require explicit confirmation before calling check/execute.
- [ ] Store returned UUID/job ID and expose manual result refresh.
- [ ] Do not start background polling in this phase; manual refresh avoids hidden repeated activity.
- [ ] Add state-transition tests for validation failure, confirmation gate and launch receipt; commit.

### Task 4: Adaptive Compose form and confirmation

**Files:**
- Modify: `feature/modules/src/main/kotlin/dev/mago/android/modules/ModulesScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Interfaces:**
- Renders current `ModulesUiState` and forwards the Task 3 actions.

- [ ] Render editable required/basic/advanced options with inline errors.
- [ ] Render compatible payload selector when available.
- [ ] Show `執行檢查` only when the module reports check support and type is exploit/auxiliary.
- [ ] Show `執行模組` for executable module types.
- [ ] Present a confirmation dialog with action, module path and redacted non-empty options.
- [ ] Present launch UUID/job ID and manual result refresh.
- [ ] Keep static UI verification to compile/Lint and manual smoke checks; commit.

### Task 5: CI verification and integration

**Files:**
- Modify only when required: `.github/workflows/android.yml`

- [ ] Ensure `feature:modules:testDebugUnitTest` is included in the risk-based test command.
- [ ] Open a PR from `feature/phase3-modules` to `main`.
- [ ] Run Android Build, Lint, core RPC tests and feature module tests.
- [ ] Fix only evidence-backed failures from GitHub Actions.
- [ ] Download and hash the successful debug APK before integration.
