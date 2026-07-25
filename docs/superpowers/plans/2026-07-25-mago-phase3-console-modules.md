# MAGO Phase 3 Console and Module Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add a read-only Metasploit module browser and an interactive RPC Console to the verified Android app.

**Architecture:** Add typed module/console models in `core:model`, domain repository interfaces in `domain:metasploit`, and MessagePack RPC adapters in `core:rpc`. Two focused Compose feature modules expose module search/details and a single-reader console. This phase does not execute modules or create payloads.

**Tech Stack:** Kotlin, Coroutines, Jetpack Compose, MessagePack RPC, existing in-memory RPC token store.

## Global Constraints

- Android 12+; compile/target SDK remain unchanged.
- RPC remains restricted to `127.0.0.1:55552/api`.
- Module browser is read-only in this phase.
- Console I/O is serialized; one repository owns each console stream.
- Tests are limited to RPC response parsing and console command normalization.

---

### Task 1: Module and Console Models

- [x] Add `MetasploitModuleType`, module summary/info/option models, and console snapshot models.
- [x] Add module and console repository interfaces.

### Task 2: RPC Services and Repositories

- [x] Add official `module.*` and `console.*` method constants.
- [x] Parse module lists, metadata, options and console responses while preserving unknown fields.
- [x] Require an authenticated in-memory token before RPC calls.
- [x] Serialize console create/read/write/destroy with a Mutex and normalize commands to CRLF.
- [x] Add focused parser and command-normalization unit tests.

### Task 3: Compose Features

- [x] Add `feature:modules` with type filtering, local name search and module detail/option display.
- [x] Add `feature:terminal` with console creation, periodic read, command input, refresh and output clear.
- [x] Do not add screenshot tests for static UI.

### Task 4: App Integration and CI

- [x] Register feature modules in Gradle and AppContainer.
- [x] Add Modules and Console destinations to phone bottom navigation and tablet navigation rail.
- [x] Run Android Build, Lint and only the affected RPC/domain unit tests in GitHub Actions.
- [x] Upload the debug APK after a green run.
