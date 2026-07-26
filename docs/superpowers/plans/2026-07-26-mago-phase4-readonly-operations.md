# MAGO Phase 4 Read-Only Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a read-only Jobs and Sessions dashboard with manual refresh and job detail.

**Architecture:** The RPC layer implements the official `job.list`, `job.info`, and `session.list` contracts. A typed domain repository feeds a new Compose feature. The feature performs no background polling and exposes no command, routing, upgrade, stop, or bulk action.

**Tech Stack:** Kotlin, Coroutines/Flow, Jetpack Compose Material 3, MessagePack RPC, JUnit/Truth, GitHub Actions.

## Global Constraints

- Android API settings remain unchanged.
- RPC remains localhost-only.
- This phase is read-only and manually refreshed.
- No session input/output, command execution, shell upgrade, routing, stop, or batch controls.
- Tests cover integer MessagePack map keys, RPC parsing, and ViewModel state only.
- Static Compose layout uses Build and Lint instead of screenshot tests.

---

### Task 1: Support Integer Session Map Keys

- Modify `MessagePackRpcCodec` so string keys remain strings and integer map keys become decimal strings.
- Keep every other key type rejected with `RPC_UNSUPPORTED_MAP_KEY`.
- Add one focused codec test.

### Task 2: Implement Typed Read-Only RPC

- Add `job.list`, `job.info`, and `session.list` methods.
- Add typed job and session models while preserving unknown fields.
- Add `MetasploitOperationsRepository` with `jobs()`, `jobInfo(jobId)`, and `sessions()`.
- Add focused RPC parsing and argument-order tests.

### Task 3: Build Operations State and UI

- Add `feature:operations` with manual `refresh()` and `selectJob()` only.
- Render Jobs and Sessions tabs, explicit loading/error/empty states, job details, and session metadata.
- Add only state-transition tests; no screenshot tests.

### Task 4: Wire and Verify

- Add the feature module, navigation destination, AppContainer repository, ViewModel, and CI test task.
- Run Bridge validation, Android Build, Lint, RPC tests, and operations tests in GitHub Actions.
- Download and hash the successful APK before integration.

## Self-Review

- The feature is read-only.
- No hidden or repeated network activity is introduced.
- No command or destructive operation is exposed.
- Testing remains risk-directed.
