# MAGO Phase 5 Workspace Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit Workspace creation and active-Workspace switching to the existing Metasploit inventory screen without exposing deletion, credentials, scanning, or autonomous operations.

**Architecture:** Extend the existing typed inventory RPC service and repository with `db.current_workspace`, `db.add_workspace`, and `db.set_workspace`. `InventoryViewModel` keeps browse selection separate from the Metasploit active Workspace and owns conservative name validation. Compose adds a create dialog, active marker, and explicit set-active action.

**Tech Stack:** Kotlin 2.4.10, MessagePack RPC, Coroutines/StateFlow, Jetpack Compose Material 3, JUnit4/Truth, GitHub Actions.

## Global Constraints

- RPC remains fixed to `http://127.0.0.1:55552/api`.
- Only `db.current_workspace`, `db.add_workspace`, and `db.set_workspace` are added.
- No Workspace deletion.
- No credential reads, host/service/vulnerability writes, scans, modules, jobs, sessions, or background polling.
- Workspace names must match `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`.
- Create and set-active are explicit user actions and are never automatically retried.
- Tests remain risk-directed: RPC argument order/result parsing, validation, and ViewModel state transitions only.

---

### Task 1: Lock Workspace RPC Contracts

**Files:** modify `RpcMethod.kt`, `RpcInventoryService.kt`, repository interface/implementation, and focused RPC tests.

- [x] Add `DB_CURRENT_WORKSPACE`, `DB_ADD_WORKSPACE`, and `DB_SET_WORKSPACE`.
- [x] Encode create/set arguments as one `RpcValue.StringValue`.
- [x] Parse current Workspace name and ID; require non-blank name and non-negative ID.
- [x] Treat only `result=success` as successful mutation.
- [x] Preserve existing localhost/token boundaries.

### Task 2: Add Explicit ViewModel State

**Files:** modify `InventoryViewModel.kt` and focused tests.

- [x] Track `activeWorkspace`, create-dialog visibility, draft name, validation error, and mutation loading/error.
- [x] Validate the conservative Workspace-name regex and duplicates locally before RPC.
- [x] Create Workspace only after explicit submit; refresh and browse-select the new Workspace without making it active automatically.
- [x] Set active Workspace only after explicit button action.
- [x] Never auto-retry mutation calls.

### Task 3: Add Compose Controls

**Files:** modify `InventoryScreen.kt`, `MagoApp.kt`, and `MainActivity.kt`.

- [x] Mark the active Workspace in the selector.
- [x] Add “新增 Workspace” dialog with inline validation.
- [x] Add “設為作用中” button when browse selection differs from active Workspace.
- [x] Disable mutation controls while an RPC mutation is in flight.
- [x] Keep inventory lists and pagination behavior unchanged.

### Task 4: CI Verification

- [ ] Ensure inventory tests remain in the risk-based CI command.
- [ ] Run Android Build, Lint, RPC tests, and inventory tests in GitHub Actions.
- [ ] Fix only evidence-backed failures.
- [ ] Download and hash the successful APK before merge.
