# MAGO Phase 3 Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 MAGO V1 的模組索引、搜尋、詳細資料、動態參數表單、相容 Payload、經人工確認的 Check／Execute、執行歷史與稽核。

**Architecture:** `core:rpc` 只處理 Rapid7 MessagePack RPC 合約與容錯解析；`core:database` 以 Room 保存離線索引、收藏、最近使用、執行歷史與稽核；`domain:metasploit` 定義純 Kotlin 模型、驗證器與 repository 介面；`feature:modules` 只使用 domain API。實際執行維持 localhost RPC、單次操作、人工確認與參數遮罩，不加入批量、公網掃描或自動攻擊鏈。

**Tech Stack:** Kotlin 2.4.10、AGP 9.3.0、Gradle 9.5.0、JDK 17、Jetpack Compose、Coroutines／Flow、Room 2.8.4、OkHttp 5.3.0、MessagePack Core 0.9.12、Truth／JUnit4。

## Global Constraints

- `minSdk = 31`、`compileSdk = 36`、`targetSdk = 36`。
- V1 RPC 僅允許 `http://127.0.0.1:55552/api`。
- UI 不得直接呼叫 RPC、Room DAO、Keystore 或 Termux Intent。
- RPC DTO 必須容忍未知欄位並保留於 `extraFields`。
- Room 只保存 App 快取、歷史與稽核，不取代 Metasploit Database。
- 敏感參數不得以明文寫入 Room、Log、稽核或一般剪貼簿。
- Check 與 Execute 必須由使用者在確認頁再次確認；不得背景自動執行或自動重試。
- 不提供批量執行、公網自動掃描、攻擊鏈或自動選擇 Exploit。
- 測試採風險導向；純 Compose 排版只做 Build、Lint 與人工 Smoke Test。

---

## File Map

```text
core/model/.../ModuleOperationModels.kt
core/rpc/.../RpcMethod.kt
core/rpc/.../service/RpcModuleService.kt
core/rpc/.../MetasploitModuleRepositoryImpl.kt
core/rpc/.../RpcModuleServiceTest.kt

domain/metasploit/.../MetasploitModuleRepository.kt
domain/metasploit/.../ModuleOptionValidator.kt
domain/metasploit/.../SensitiveOptionPolicy.kt
domain/metasploit/.../ModuleOptionValidatorTest.kt

core/database/.../entity/ModuleIndexEntity.kt
core/database/.../entity/ModuleSearchFtsEntity.kt
core/database/.../entity/ModuleFavoriteEntity.kt
core/database/.../entity/ModuleRecentEntity.kt
core/database/.../entity/ModuleExecutionEntity.kt
core/database/.../entity/AuditEventEntity.kt
core/database/.../dao/ModuleCatalogDao.kt
core/database/.../dao/ModuleHistoryDao.kt
core/database/.../CachedMetasploitModuleRepository.kt
core/database/.../ModuleDatabaseMapper.kt
core/database/.../MagoDatabase.kt

feature/modules/.../ModulesViewModel.kt
feature/modules/.../ModulesScreen.kt
feature/modules/.../ModuleOptionField.kt
feature/modules/.../ModuleRunConfirmationSheet.kt
feature/modules/.../ModuleExecutionHistory.kt

app/.../AppContainer.kt
.github/workflows/android.yml
README.md
```

### Task 1: Lock the Rapid7 Module RPC Contract

**Files:** modify `RpcMethod.kt`, create `ModuleOperationModels.kt`, modify `MetasploitModuleRepository.kt`, add focused RPC tests.

**Produces:**

```kotlin
data class ModuleSearchQuery(
    val text: String,
    val types: Set<MetasploitModuleType> = emptySet(),
    val ranks: Set<String> = emptySet(),
    val platforms: Set<String> = emptySet(),
    val architectures: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val recentOnly: Boolean = false,
)

enum class ModuleRunMode { CHECK, EXECUTE }

data class ModuleRunRequest(
    val type: MetasploitModuleType,
    val name: String,
    val options: Map<String, String>,
    val mode: ModuleRunMode,
    val workspace: String?,
    val correlationId: String,
    val userConfirmed: Boolean,
)

data class ModuleRunReceipt(
    val jobId: Long?,
    val uuid: String,
    val extraFields: Map<String, RpcValue> = emptyMap(),
)

enum class ModuleRunStatus { READY, RUNNING, COMPLETED, ERRORED, UNKNOWN }

data class ModuleRunResult(
    val uuid: String,
    val status: ModuleRunStatus,
    val summary: String?,
    val error: String?,
    val extraFields: Map<String, RpcValue> = emptyMap(),
)
```

- [ ] Write a failing test asserting the new methods require a token.
- [ ] Run `gradle --no-daemon :core:rpc:testDebugUnitTest --tests '*RpcModuleServiceTest*'`; expect compilation failure.
- [ ] Add exact methods: `module.search`, `module.options`, `module.compatible_payloads`, `module.target_compatible_payloads`, `module.check`, `module.execute`, `module.results`, `module.running_stats`, `module.ack`.
- [ ] Add the operation models above.
- [ ] Expand `MetasploitModuleRepository` with `search`, `options`, `compatiblePayloads`, `check`, `execute`, and `result`.
- [ ] Run `:core:rpc:testDebugUnitTest`; expect PASS.
- [ ] Commit `feat: define module operation RPC contract`.

### Task 2: Validate Options and Redact Sensitive Values

**Files:** create `ModuleOptionValidator.kt`, `SensitiveOptionPolicy.kt`, and tests.

**Produces:**

```kotlin
data class ModuleValidationError(val optionName: String, val message: String)
data class ModuleValidationResult(
    val normalizedValues: Map<String, String>,
    val errors: List<ModuleValidationError>,
) { val isValid: Boolean get() = errors.isEmpty() }
```

Rules: required, bool normalization, signed integer, port `1..65535`, exact enum membership, control-character rejection for address/range, trimmed string fallback for unknown option types. Sensitive-name regex covers `PASSWORD`, `PASS`, `TOKEN`, `API_KEY`, `PRIVATE_KEY`, `SMBPASS`, `DB_PASSWORD`, `SECRET`, and `CREDENTIAL`.

- [ ] Write tests for required, port, enum, bool normalization and redaction.
- [ ] Run `:domain:metasploit:testDebugUnitTest`; expect RED.
- [ ] Implement validator and policy.
- [ ] Run focused tests; expect GREEN.
- [ ] Commit `feat: validate and redact module options`.

### Task 3: Implement Search, Options, Payload, Check, Execute and Results RPC

**Files:** modify `RpcModuleService.kt`, `MetasploitModuleRepositoryImpl.kt`, add contract fixtures.

Exact argument order:

```text
module.search(token, query)
module.options(token, type, name)
module.compatible_payloads(token, exploitName)
module.target_compatible_payloads(token, exploitName, target)
module.check(token, type, name, optionsMap)
module.execute(token, type, name, optionsMap)
module.results(token, uuid)
```

- [ ] Add fixture tests for unknown search fields, options, payloads, receipts and result states.
- [ ] Add a test proving `userConfirmed=false` performs zero transport calls.
- [ ] Encode option maps as `RpcValue.MapValue`; never interpolate Shell.
- [ ] Preserve unknown fields; reject missing required `uuid`.
- [ ] Run `:core:rpc:testDebugUnitTest`; expect GREEN.
- [ ] Commit `feat: implement module operation RPC methods`.

### Task 4: Add Room Module Index, FTS, Favorites, Recent and History

**Files:** create entities, DAOs, mapper and cached repository; modify `MagoDatabase.kt`; add migration and mapper/repository tests.

Entities:

```text
module_index(type,name,displayName,description,rank,platformsText,architecturesText,authorsText,refreshedAt)
module_search_fts(name,displayName,description,platformsText,architecturesText,authorsText)
module_favorite(type,name,createdAt)
module_recent(type,name,lastOpenedAt)
module_execution(correlationId,mode,type,name,workspace,status,jobId,uuid,redactedParameters,createdAt,updatedAt)
audit_event(correlationId,category,action,moduleName,workspace,result,redactedParameters,createdAt)
```

- [ ] Write mapper and offline-fallback tests.
- [ ] Add Room FTS and DAO queries with bounded result counts.
- [ ] Add explicit migration `1 → 2`; destructive migration is forbidden.
- [ ] Implement online success → cache; remote failure + cached data → cached result.
- [ ] Redact before inserting execution/audit rows.
- [ ] Run `:core:database:testDebugUnitTest :core:database:lintDebug`.
- [ ] Commit `feat: cache module catalog and execution history`.

### Task 5: Wire Cached Repository and Offline State

**Files:** modify `AppContainer.kt`, `ModulesViewModel.kt`; add ViewModel tests.

`AppContainer` creates the RPC repository, then wraps it with `CachedMetasploitModuleRepository`. `ModulesUiState` adds option values/errors, payloads, favorites, offline state, pending confirmation, running state and last receipt.

- [ ] Test 250 ms search debounce, cancellation on type change, offline banner, recent recording and disabled operation while invalid.
- [ ] Wire Room DAOs and cached repository.
- [ ] Run `:feature:modules:testDebugUnitTest :app:assembleDebug`.
- [ ] Commit `feat: wire offline module catalog state`.

### Task 6: Build Search, Detail, Dynamic Form and Payload UI

**Files:** modify `ModulesScreen.kt`; create `ModuleOptionField.kt`.

Widget mapping:

```text
bool → Switch
enum → ExposedDropdownMenu
integer/port → numeric keyboard
address/addressrange → text field with inline validation
path/string/unknown → text field
sensitive name → PasswordVisualTransformation; no copy action
```

Detail tabs: `概覽 | 參數 | 目標／Payload | 參考資料 | 執行紀錄`. Phone uses list/detail navigation; width ≥ 700 dp uses split pane.

- [ ] Add filters, favorites/recent chips, offline/stale banner and refresh.
- [ ] Add dynamic controls and reset-to-RPC-default behavior.
- [ ] Compile and lint feature/app.
- [ ] Commit `feat: add module search and option form UI`.

### Task 7: Add Explicit Check and Execute Confirmation

**Files:** create `ModuleRunConfirmationSheet.kt`; modify ViewModel/Screen and tests.

The confirmation summary shows module, action, workspace, target, payload, redacted parameters and authorization statement. Confirm remains disabled until the user checks: `我確認僅在本人擁有或明確授權的環境執行`. There is no remembered approval.

- [ ] Test cancel performs no RPC call.
- [ ] Test confirm creates one correlation ID and exactly one operation call.
- [ ] Test summary never exposes sensitive values.
- [ ] Do not automatically retry Check/Execute.
- [ ] Run feature and database tests.
- [ ] Commit `feat: require confirmation for module operations`.

### Task 8: Add Result Polling, Execution History, CI and Docs

**Files:** create `ModuleExecutionHistory.kt`; modify ViewModel, workflow and README.

Polling: only while visible; delays `1,2,3,5,8` seconds; stop on completed/errored, exit/background or two minutes; one coroutine per UUID.

- [ ] Add history UI with timestamp, mode, module, workspace, status, job ID/UUID and redacted summary.
- [ ] Extend CI with `domain:metasploit` and `feature:modules` tests.
- [ ] Document offline search, option widgets, confirmation, redaction and no bulk/autonomous execution.
- [ ] Run Bridge contract plus full Android build/lint/test command.
- [ ] Commit `feat: complete module execution history and verification`.

## Self-Review

- Spec coverage: index, search, detail, options, FTS, favorites, recent, payloads, Check, Execute, history and audit map to Tasks 1–8.
- Safety coverage: localhost-only transport, explicit confirmation, no bulk/autonomous behavior and pre-persistence redaction.
- Migration coverage: explicit `1 → 2`, no destructive fallback.
- Compatibility coverage: unknown RPC fields are preserved and optional malformed fields degrade safely.
- Testing budget: tests target RPC contracts, validation, redaction, migration, fallback and operation gating; static Compose layout is not over-tested.
- Placeholder scan: no TBD/TODO or unspecified implementation steps remain.
