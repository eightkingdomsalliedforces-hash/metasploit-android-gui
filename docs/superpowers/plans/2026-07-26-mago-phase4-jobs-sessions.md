# MAGO Phase 4 Jobs and Sessions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a controlled graphical operations page for listing, inspecting and stopping individual Metasploit Jobs and Sessions, plus explicitly authorized single-session interactive I/O.

**Architecture:** Extend the existing typed MessagePack RPC layer with job and session services. Integer MessagePack map keys are normalized to decimal strings with duplicate-key rejection. The feature keeps all session input/output in memory, serializes I/O per session with a mutex, and performs reads only when the user requests them.

**Tech Stack:** Kotlin, Coroutines/Flow, Jetpack Compose Material 3, MessagePack RPC, JUnit/Truth, GitHub Actions.

## Global Constraints

- Android minimum remains API 31; compileSdk and targetSdk remain unchanged.
- RPC remains fixed to `http://127.0.0.1:55552/api`.
- No bulk job/session actions, background session polling, automatic post-exploitation or credential helpers.
- Stop operations require explicit confirmation for exactly one Job or Session.
- Session interaction requires an ephemeral authorized-use acknowledgement and is never persisted.
- Session input and output are kept only in ViewModel memory and excluded from diagnostics and Room.
- Every Session read/write goes through one mutex per Session ID.
- Session writes are limited to 8 KiB UTF-8, reject control characters, and are never automatically retried.
- Tests remain risk-directed: codec map keys, RPC parsing, confirmation boundaries and I/O serialization only.

---

### Task 1: Integer MessagePack map keys

**Files:**
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/MessagePackRpcCodec.kt`
- Modify: `core/rpc/src/test/kotlin/dev/mago/android/rpc/MessagePackRpcCodecTest.kt`

**Interfaces:**
- Produces: decoded `RpcValue.MapValue` with integer keys represented as decimal strings.

- [ ] Add a test decoding a map with integer Session ID keys.
- [ ] Accept string and integer map keys only.
- [ ] Reject key normalization collisions and all other key types.
- [ ] Run `:core:rpc:testDebugUnitTest` and commit.

### Task 2: Typed Job and Session RPC

**Files:**
- Create: `core/model/src/main/kotlin/dev/mago/android/model/MetasploitOperation.kt`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt`
- Create: `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcJobService.kt`
- Create: `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcSessionService.kt`
- Test: `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcJobServiceTest.kt`
- Test: `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcSessionServiceTest.kt`

**Interfaces:**
- Produces: `MetasploitJobSummary`, `MetasploitJobInfo`, `MetasploitSessionSummary`, `MetasploitSessionRead`.
- Produces methods for `job.list`, `job.info`, `job.stop`, `session.list`, `session.stop`, `session.interactive_read`, `session.interactive_write`.

- [ ] Parse Job list keys and Job info datastore while preserving unknown fields.
- [ ] Parse Session list metadata without assuming optional fields exist.
- [ ] Require explicit confirmation before stop or interactive write; reject before transport call.
- [ ] Validate Session input as non-empty, no control characters and at most 8 KiB UTF-8.
- [ ] Parse success results and read data.
- [ ] Add focused RPC contract tests and commit.

### Task 3: Repositories and per-session I/O serialization

**Files:**
- Create: `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationRepository.kt`
- Create: `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitJobRepositoryImpl.kt`
- Create: `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitSessionRepositoryImpl.kt`
- Create: `core/rpc/src/main/kotlin/dev/mago/android/rpc/SessionIoCoordinator.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/AppContainer.kt`
- Test: `core/rpc/src/test/kotlin/dev/mago/android/rpc/SessionIoCoordinatorTest.kt`

**Interfaces:**
- Produces: `MetasploitJobRepository` and `MetasploitSessionRepository`.
- Produces: `SessionIoCoordinator.withSessionLock(sessionId, block)`.

- [ ] Gate every call on the in-memory RPC token.
- [ ] Serialize read and write calls by Session ID while allowing different Session IDs independently.
- [ ] Do not retry stop or write calls.
- [ ] Add one concurrency-focused coordinator test and commit.

### Task 4: Operations ViewModel

**Files:**
- Create: `feature/operations/build.gradle.kts`
- Create: `feature/operations/src/main/kotlin/dev/mago/android/operations/OperationsViewModel.kt`
- Test: `feature/operations/src/test/kotlin/dev/mago/android/operations/OperationsViewModelTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Produces: `OperationsUiState` with Jobs, Sessions, selected details, stop confirmation and one ephemeral interactive Session.
- Produces actions: refresh Jobs/Sessions, select, request/cancel/confirm stop, request interaction, acknowledge authorization, send, read, clear and close interaction.

- [ ] Load Jobs and Sessions only on explicit refresh.
- [ ] Require a confirmation object before stopping one item.
- [ ] Require authorization acknowledgement before opening interactive controls.
- [ ] Keep command/output only in state and clear them when interaction closes.
- [ ] Never perform background reads.
- [ ] Add tests for stop confirmation, authorization gate, manual read and memory clearing; commit.

### Task 5: Adaptive Operations UI and navigation

**Files:**
- Create: `feature/operations/src/main/kotlin/dev/mago/android/operations/OperationsScreen.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/dev/mago/android/navigation/MagoDestination.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

**Interfaces:**
- Renders `OperationsUiState` and forwards Task 4 actions.

- [ ] Add an Operations destination labelled `作業`.
- [ ] Render Jobs and Sessions tabs with manual refresh.
- [ ] Show one-item stop confirmation with item ID and name/type.
- [ ] Show Session authorization dialog before interactive controls.
- [ ] Render in-memory output, command input, Send and manual Read buttons.
- [ ] Keep static UI validation to compile, Lint and manual smoke checks; commit.

### Task 6: CI verification and integration

**Files:**
- Modify: `.github/workflows/android.yml`

- [ ] Add `:feature:operations:testDebugUnitTest` to the risk-directed CI command.
- [ ] Open a PR from `feature/phase4-jobs-sessions` to `main`.
- [ ] Run Build, Lint, core RPC and operations tests.
- [ ] Fix only evidence-backed failures.
- [ ] Download and hash the successful debug APK before merge.
