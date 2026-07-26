# MAGO Phase 8A Single Job and Session Stop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` task by task. Use `superpowers:test-driven-development` for every production change and `superpowers:verification-before-completion` before completion claims.

**Goal:** Allow the user to stop exactly one currently listed Metasploit Job or Session after a second confirmation, then perform one atomic Jobs/Sessions verification refresh.

**Architecture:** Extend the existing `RpcOperationsService` and `MetasploitOperationsRepository`. Keep confirmation, cross-operation locking, one stop call, one verification refresh, and safe feedback in `DashboardViewModel`. Compose receives typed state and callbacks only.

**Tech stack:** Kotlin, Android, Jetpack Compose Material 3, ViewModel/StateFlow, Kotlin coroutines test, JUnit 4, Google Truth, MessagePack RPC, GitHub Actions.

## Non-negotiable boundaries

- Create `feature/phase8a-single-operation-stop` from `design/phase8a-single-operation-stop` in an isolated worktree.
- Add only `job.stop` and `session.stop`.
- A visible confirmation dialog and `userConfirmed = true` at the RPC service boundary are both required.
- Unconfirmed, invalid-ID, missing-token, missing-target, and non-READY paths perform zero stop transport calls.
- Each accepted confirmation produces at most one stop RPC.
- Keep `OkHttpRpcTransport.retryOnConnectionFailure(false)` unchanged.
- Do not retry the stop, verification refresh, or connection automatically.
- Stop confirmation, active stopping, maintenance confirmation, and active maintenance must not overlap as actionable surfaces.
- After a successful stop RPC, call `jobs()` once and `sessions()` once. Apply neither list unless both succeed.
- A failed verification read preserves both old lists and the selected Job.
- Do not log or persist IDs, stop targets, tokens, raw responses, technical messages, diagnostic data, or exception text.
- Do not add Session I/O, console input, Meterpreter APIs, arbitrary RPC methods, batch stop, stop-all, polling, queues, background work, permissions, routes, modules, persistence, or signed-release changes.

## Planned files

- `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt`
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt`
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt`
- `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt`
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt`
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt`
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt`
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt`
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/OperationStopStateTest.kt`
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`
- `README.md`

---

## Task 1 — Strict Job/Session stop RPC contract

**Modify:**
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt`
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt`
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt`

### 1.1 RED: make the test transport observable

Replace the existing test transport with:

```kotlin
private class FakeTransport(
    private val response: AppResult<RpcValue>,
) : RpcTransport {
    constructor(response: RpcValue) : this(AppResult.Success(response))

    var calls = 0
    var lastMethod: RpcMethod? = null
    var lastToken: String? = null
    var lastArguments: List<RpcValue> = emptyList()

    override suspend fun call(
        method: RpcMethod,
        token: String?,
        arguments: List<RpcValue>,
    ): AppResult<RpcValue> {
        calls += 1
        lastMethod = method
        lastToken = token
        lastArguments = arguments
        return response
    }
}

private fun successResponse(): RpcValue = RpcValue.MapValue(
    mapOf("result" to RpcValue.StringValue("success")),
)

private fun assertFailureCode(result: AppResult<*>, code: String) {
    assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    assertThat((result as AppResult.Failure).error.errorCode).isEqualTo(code)
}
```

Add RED tests proving:

```kotlin
@Test fun `unconfirmed job stop performs zero calls`() = runTest { /* code below */ }
@Test fun `invalid job IDs perform zero calls`() = runTest { /* bad, -1, overflow */ }
@Test fun `unconfirmed session stop performs zero calls`() = runTest { /* false */ }
@Test fun `negative session ID performs zero calls`() = runTest { /* -1 */ }
```

Use these exact assertions:

```kotlin
val transport = FakeTransport(successResponse())
val result = RpcOperationsService(transport).stopJob("token", "4", false)
assertFailureCode(result, "RPC_JOB_CONFIRMATION_REQUIRED")
assertThat(transport.calls).isEqualTo(0)
```

For invalid Job IDs, iterate over:

```kotlin
listOf("bad", "-1", "9223372036854775808")
```

Expected RED command:

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.service.RpcOperationsServiceTest'
```

Expected failure: `stopJob`, `stopSession`, `JOB_STOP`, and `SESSION_STOP` do not exist.

### 1.2 GREEN: add fixed methods and strict parsing

Add beside the existing Job/Session methods:

```kotlin
val JOB_STOP = RpcMethod("job.stop")
val SESSION_STOP = RpcMethod("session.stop")
```

Add to `RpcOperationsService`:

```kotlin
suspend fun stopJob(
    token: String,
    jobId: String,
    userConfirmed: Boolean,
): AppResult<Unit> {
    if (!userConfirmed) {
        return invalid("RPC_JOB_CONFIRMATION_REQUIRED", "停止 Job 需要使用者明確確認", false)
    }
    val parsedJobId = jobId.toLongOrNull()?.takeIf { it >= 0 }
        ?: return invalid("RPC_JOB_ID_INVALID", "Job ID 不正確", false)
    return when (
        val response = transport.call(
            RpcMethod.JOB_STOP,
            token,
            listOf(RpcValue.IntValue(parsedJobId)),
        )
    ) {
        is AppResult.Failure -> response
        is AppResult.Success -> parseStopResult(
            response.value,
            invalidCode = "RPC_JOB_STOP_RESPONSE_INVALID",
            failedCode = "RPC_JOB_STOP_FAILED",
            failedMessage = "Metasploit 無法停止 Job",
        )
    }
}

suspend fun stopSession(
    token: String,
    sessionId: Int,
    userConfirmed: Boolean,
): AppResult<Unit> {
    if (!userConfirmed) {
        return invalid("RPC_SESSION_CONFIRMATION_REQUIRED", "停止 Session 需要使用者明確確認", false)
    }
    if (sessionId < 0) {
        return invalid("RPC_SESSION_ID_INVALID", "Session ID 不正確", false)
    }
    return when (
        val response = transport.call(
            RpcMethod.SESSION_STOP,
            token,
            listOf(RpcValue.IntValue(sessionId.toLong())),
        )
    ) {
        is AppResult.Failure -> response
        is AppResult.Success -> parseStopResult(
            response.value,
            invalidCode = "RPC_SESSION_STOP_RESPONSE_INVALID",
            failedCode = "RPC_SESSION_STOP_FAILED",
            failedMessage = "Metasploit 無法停止 Session",
        )
    }
}

private fun parseStopResult(
    value: RpcValue,
    invalidCode: String,
    failedCode: String,
    failedMessage: String,
): AppResult<Unit> {
    val map = (value as? RpcValue.MapValue)?.value
        ?: return invalid(invalidCode, "Metasploit 停止回應格式不正確")
    val result = (map["result"] as? RpcValue.StringValue)?.value
        ?: return invalid(invalidCode, "Metasploit 停止回應格式不正確")
    return if (result.equals("success", ignoreCase = true)) {
        AppResult.Success(Unit)
    } else {
        invalid(failedCode, failedMessage)
    }
}
```

Add tests proving:

- Job uses `JOB_STOP`, token `token`, one `IntValue(4)`, and accepts `SuCcEsS`.
- Session uses `SESSION_STOP` and one `IntValue(7)`.
- Non-map, missing `result`, and non-string `result` use `*_STOP_RESPONSE_INVALID`.
- String `result=failed` uses `*_STOP_FAILED`.
- A transport `AppResult.Failure` is returned as the same instance and transport call count remains one.

Run:

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest
```

Commit:

```bash
git add core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt \
  core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt \
  core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt
git commit -m "feat: add confirmed single operation stop RPC"
```

---

## Task 2 — Token-gated repository methods

**Modify:**
- `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt`
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt`

**Create:**
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt`

### 2.1 RED: repository boundary tests

Create a `RecordingTransport` that returns:

```kotlin
AppResult.Success(
    RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("success"))),
)
```

Create the exact token fake:

```kotlin
private class FakeTokenStore(private val token: String?) : RpcTokenStore {
    override fun get(): String? = token
    override fun set(token: String) = Unit
    override fun clear() = Unit
}
```

Add tests proving:

1. `FakeTokenStore(null)` + `stopJob("4", true)` returns `RPC_NOT_AUTHENTICATED` and transport calls remain zero.
2. Authenticated Job stop makes one `JOB_STOP` call with `IntValue(4)`.
3. Authenticated Session stop makes one `SESSION_STOP` call with `IntValue(7)`.

Run RED:

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.MetasploitOperationsRepositoryImplTest'
```

### 2.2 GREEN: extend interface and implementation

Add to `MetasploitOperationsRepository`:

```kotlin
suspend fun stopJob(jobId: String, userConfirmed: Boolean): AppResult<Unit>
suspend fun stopSession(sessionId: Int, userConfirmed: Boolean): AppResult<Unit>
```

Add to `MetasploitOperationsRepositoryImpl`:

```kotlin
override suspend fun stopJob(
    jobId: String,
    userConfirmed: Boolean,
): AppResult<Unit> = token()?.let {
    service.stopJob(it, jobId, userConfirmed)
} ?: notAuthenticated()

override suspend fun stopSession(
    sessionId: Int,
    userConfirmed: Boolean,
): AppResult<Unit> = token()?.let {
    service.stopSession(it, sessionId, userConfirmed)
} ?: notAuthenticated()
```

Do not add logging, persistence, retry, exception wrapping, or response transformation.

Run and commit:

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest
git add domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt \
  core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt \
  core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt
git commit -m "feat: expose authenticated operation stop repository"
```

---

## Task 3 — Typed stop state and atomic Operations loading

**Create:**
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt`
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/OperationStopStateTest.kt`

**Modify:**
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt` (`DashboardUiState` only)
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`

### 3.1 Add typed state and a pure presence predicate

```kotlin
package dev.mago.android.dashboard

import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary

sealed interface OperationStopTarget {
    val displayId: String

    data class Job(val id: String, val name: String) : OperationStopTarget {
        override val displayId: String = id
    }

    data class Session(
        val id: Int,
        val description: String,
        val sourceModule: String?,
    ) : OperationStopTarget {
        override val displayId: String = id.toString()
    }
}

data class OperationStopError(
    val title: String,
    val userMessage: String?,
)

internal fun OperationStopTarget.existsIn(
    jobs: List<MetasploitJobSummary>,
    sessions: List<MetasploitSessionSummary>,
): Boolean = when (this) {
    is OperationStopTarget.Job -> jobs.any { it.id == id }
    is OperationStopTarget.Session -> sessions.any { it.id == id }
}
```

Test both matching and missing Job/Session IDs with complete `MetasploitSessionSummary` fixtures.

### 3.2 Replace the Dashboard test repository with a programmable fake

The fake must contain:

```kotlin
var jobsResult: AppResult<List<MetasploitJobSummary>> = AppResult.Success(defaultJobs())
var sessionsResult: AppResult<List<MetasploitSessionSummary>> = AppResult.Success(defaultSessions())
var jobInfoResult: AppResult<MetasploitJobInfo> = AppResult.Success(defaultJobInfo("2"))
var stopJobResult: AppResult<Unit> = AppResult.Success(Unit)
var stopSessionResult: AppResult<Unit> = AppResult.Success(Unit)
var stopJobGate: CompletableDeferred<Unit>? = null

var jobsCalls = 0
var sessionsCalls = 0
val jobInfoCalls = mutableListOf<String>()
val stopJobCalls = mutableListOf<Pair<String, Boolean>>()
val stopSessionCalls = mutableListOf<Pair<Int, Boolean>>()
```

Methods:

```kotlin
override suspend fun jobs() = jobsResult.also { jobsCalls += 1 }
override suspend fun sessions() = sessionsResult.also { sessionsCalls += 1 }
override suspend fun jobInfo(jobId: String) = jobInfoResult.also { jobInfoCalls += jobId }

override suspend fun stopJob(jobId: String, userConfirmed: Boolean): AppResult<Unit> {
    stopJobCalls += jobId to userConfirmed
    stopJobGate?.await()
    return stopJobResult
}

override suspend fun stopSession(sessionId: Int, userConfirmed: Boolean): AppResult<Unit> {
    stopSessionCalls += sessionId to userConfirmed
    return stopSessionResult
}
```

Use default fixtures:

```kotlin
fun defaultJobs() = listOf(MetasploitJobSummary("2", "Example Job"))

fun defaultJobInfo(id: String) = MetasploitJobInfo(
    id = id,
    name = "Example Job",
    startTimeEpochSeconds = 100,
    uriPath = null,
    datastore = emptyMap(),
    extraFields = emptyMap(),
)

fun defaultSessions() = listOf(
    MetasploitSessionSummary(
        id = 7,
        type = "meterpreter",
        description = "Meterpreter",
        info = "Authorized lab",
        workspace = "default",
        sessionHost = "192.0.2.10",
        sessionPort = 445,
        targetHost = null,
        username = null,
        uuid = null,
        exploitUuid = null,
        viaExploit = "exploit/multi/handler",
        viaPayload = null,
        architecture = "x64",
        platform = "windows",
        tunnelLocal = null,
        tunnelPeer = null,
        routes = emptyList(),
        extraFields = emptyMap(),
    ),
)
```

### 3.3 RED: atomic refresh and confirmation-only behavior

Add tests:

1. Initial load succeeds, then Jobs fails and Sessions returns empty; manual refresh preserves both old lists.
2. `requestStopJob("2")` exposes `OperationStopTarget.Job("2", "Example Job")` and makes zero stop calls.
3. `cancelStop()` clears confirmation and makes zero stop calls.
4. `requestStopSession(99)` exposes a missing-target error and makes zero stop calls.
5. While stop confirmation is open, refresh, Job detail, maintenance request/confirmation, and another stop request make no calls or state transitions.
6. A maintenance confirmation already open prevents a stop confirmation.

Run RED:

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
```

### 3.4 GREEN: atomic loader, request/cancel, and cross-confirmation guards

Extend `OperationsSnapshot`:

```kotlin
val stopConfirmation: OperationStopTarget? = null,
val stoppingTarget: OperationStopTarget? = null,
val stopMessage: String? = null,
val stopError: OperationStopError? = null,
```

Add:

```kotlin
private sealed interface OperationsLoadResult {
    data class Success(
        val jobs: List<MetasploitJobSummary>,
        val sessions: List<MetasploitSessionSummary>,
    ) : OperationsLoadResult
    data class Failure(val userMessage: String) : OperationsLoadResult
}

private suspend fun loadOperationsSnapshot(): OperationsLoadResult {
    val jobsResult = operationsRepository.jobs()
    val sessionsResult = operationsRepository.sessions()
    if (jobsResult is AppResult.Success && sessionsResult is AppResult.Success) {
        return OperationsLoadResult.Success(jobsResult.value, sessionsResult.value)
    }
    val messages = buildList {
        if (jobsResult is AppResult.Failure) add(jobsResult.error.userMessage)
        if (sessionsResult is AppResult.Failure) add(sessionsResult.error.userMessage)
    }
    return OperationsLoadResult.Failure(
        messages.distinct().joinToString("\n").ifBlank { "無法取得 Jobs 與 Sessions" },
    )
}
```

`refreshOperations()` must:

- Return while loading, stop confirmation/stopping, maintenance confirmation/loading.
- Clear old stop message/error only when the refresh is accepted.
- Preserve current lists while loading.
- Replace both lists and clear selected Job only when both reads succeed.
- Preserve both lists and selected Job when either read fails.

Add one shared request guard:

```kotlin
private fun stopRequestBlocked(snapshot: OperationsSnapshot): Boolean =
    snapshot.loading ||
        snapshot.stopConfirmation != null ||
        snapshot.stoppingTarget != null ||
        maintenance.value.loading ||
        maintenance.value.confirmation != null
```

`requestStopJob` and `requestStopSession` resolve targets only from the current in-memory lists. Missing targets set these exact titles:

```text
此 Job 已不在目前列表中，請重新整理。
此 Session 已不在目前列表中，請重新整理。
```

`cancelStop()` clears confirmation and performs no RPC.

Expose now through `DashboardUiState` and both state constructors:

```kotlin
val stopConfirmation: OperationStopTarget? = null,
val stopMessage: String? = null,
val stopError: OperationStopError? = null,
val onRequestStopJob: (String) -> Unit = {},
val onRequestStopSession: (Int) -> Unit = {},
val onCancelStop: () -> Unit = {},
```

Guard `selectJob`, `requestMaintenance`, and `confirmMaintenance` while stop confirmation/stopping is active.

Run and commit:

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
git add feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt \
  feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/OperationStopStateTest.kt \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt \
  feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt
git commit -m "feat: add atomic dashboard stop confirmation state"
```

---

## Task 4 — Confirmed stop state machine and global lock

**Modify:**
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt` (`DashboardUiState` only)
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`

### 4.1 RED: mandatory state-machine tests

Add tests proving all of the following:

1. Non-READY confirmation performs zero stop calls and clears confirmation with `RPC 環境尚未就緒，未送出停止要求。`.
2. A target removed after opening the confirmation performs zero stop calls:
   - Configure initial `jobsResult` with a mutable Job list.
   - Load the ViewModel and open Job confirmation.
   - Clear that same mutable list before `confirmStop()`.
   - Assert no stop call and a missing-target error.
3. Successful Job stop calls `stopJob("2", true)` once, then Jobs once and Sessions once.
4. Stop failure performs zero verification reads, exposes `無法停止 Session #7`, preserves safe `userMessage`, and clears the global lock.
5. Verification failure preserves both old lists and selected Job and shows `停止要求已成功送出，但無法確認最新狀態。請手動重新整理。`.
6. Target still present after verification shows `停止要求已成功送出，但該項目仍出現在最新列表中。`.
7. Stopping the selected Job clears its detail after successful refresh.
8. Stopping a Session retains an unrelated selected Job when that Job remains.
9. During a suspended stop, a second stop, refresh, detail request, maintenance request, and maintenance confirmation are all ignored.
10. After a completed stop, an accepted manual refresh clears the old stop message and stop error.
11. Every terminal success/failure branch clears `stoppingTarget`.

For the active-stop lock test, use:

```kotlin
val gate = CompletableDeferred<Unit>()
repository.stopJobGate = gate
viewModel.requestStopJob("2")
viewModel.confirmStop()
runCurrent()
assertThat(viewModel.uiState.value.stoppingTarget)
    .isEqualTo(OperationStopTarget.Job("2", "Example Job"))
```

Before completing the gate, invoke the blocked actions and assert call counters remain unchanged. Then:

```kotlin
gate.complete(Unit)
advanceUntilIdle()
assertThat(viewModel.uiState.value.stoppingTarget).isNull()
```

Run RED:

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
```

### 4.2 GREEN: expose and implement confirmed stopping

Add to `DashboardUiState` and both constructors:

```kotlin
val stoppingTarget: OperationStopTarget? = null,
val onConfirmStop: () -> Unit = {},
```

Implement:

```kotlin
fun confirmStop() {
    val snapshot = operations.value
    val target = snapshot.stopConfirmation ?: return
    if (
        snapshot.loading || snapshot.stoppingTarget != null ||
        maintenance.value.loading || maintenance.value.confirmation != null
    ) return
    if (coordinator.state.value.stage != InstallationStage.READY) {
        operations.value = snapshot.copy(
            stopConfirmation = null,
            stopError = OperationStopError("RPC 環境尚未就緒，未送出停止要求。", null),
        )
        return
    }
    if (!target.existsIn(snapshot.jobs, snapshot.sessions)) {
        operations.value = snapshot.copy(
            stopConfirmation = null,
            stopError = OperationStopError(
                title = when (target) {
                    is OperationStopTarget.Job -> "此 Job 已不在目前列表中，請重新整理。"
                    is OperationStopTarget.Session -> "此 Session 已不在目前列表中，請重新整理。"
                },
                userMessage = null,
            ),
        )
        return
    }

    operations.value = snapshot.copy(
        stopConfirmation = null,
        stoppingTarget = target,
        stopMessage = null,
        stopError = null,
        error = null,
    )
    viewModelScope.launch {
        val result = when (target) {
            is OperationStopTarget.Job -> operationsRepository.stopJob(target.id, true)
            is OperationStopTarget.Session -> operationsRepository.stopSession(target.id, true)
        }
        when (result) {
            is AppResult.Failure -> operations.value = operations.value.copy(
                stoppingTarget = null,
                stopError = OperationStopError(
                    title = when (target) {
                        is OperationStopTarget.Job -> "無法停止 Job #${target.id}"
                        is OperationStopTarget.Session -> "無法停止 Session #${target.id}"
                    },
                    userMessage = result.error.userMessage,
                ),
            )
            is AppResult.Success -> applyPostStopRefresh(target)
        }
    }
}
```

Implement one verification load:

```kotlin
private suspend fun applyPostStopRefresh(target: OperationStopTarget) {
    when (val result = loadOperationsSnapshot()) {
        is OperationsLoadResult.Failure -> operations.value = operations.value.copy(
            stoppingTarget = null,
            stopMessage = "停止要求已成功送出，但無法確認最新狀態。請手動重新整理。",
        )
        is OperationsLoadResult.Success -> {
            val targetPresent = target.existsIn(result.jobs, result.sessions)
            val selectedJob = operations.value.selectedJob
                ?.takeIf { selected -> target !is OperationStopTarget.Job || selected.id != target.id }
                ?.takeIf { selected -> result.jobs.any { it.id == selected.id } }
            operations.value = operations.value.copy(
                jobs = result.jobs,
                sessions = result.sessions,
                selectedJob = selectedJob,
                stoppingTarget = null,
                error = null,
                stopMessage = if (targetPresent) {
                    "停止要求已成功送出，但該項目仍出現在最新列表中。"
                } else when (target) {
                    is OperationStopTarget.Job -> "Job #${target.id} 已停止"
                    is OperationStopTarget.Session -> "Session #${target.id} 已停止"
                },
            )
        }
    }
}
```

Cross-operation guards:

- `selectJob` returns while Operations is loading, stop confirmation/stopping, maintenance confirmation/loading.
- `refreshOperations` and stop requests return while maintenance confirmation/loading.
- `requestMaintenance` and `confirmMaintenance` return while stop confirmation/stopping.
- No ignored action is replayed.

Run and commit:

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
git add feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt \
  feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt
git commit -m "feat: enforce single operation stop state machine"
```

---

## Task 5 — Compose controls, documentation, and complete verification

**Modify:**
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt`
- `README.md`

### 5.1 Render confirmation

Add an `AlertDialog` after the existing maintenance dialog.

Job text:

```text
確認停止 Job #<id>？
名稱：<name>
停止後無法由 MAGO 復原。操作只會送出一次，不會自動重試。
```

Session text:

```text
確認停止 Session #<id>？
來源模組：<sourceModule or 尚未取得>
描述：<description or 尚未取得>
停止後此 Session 可能無法再次連線。操作只會送出一次，不會自動重試。
```

Buttons:

```text
取消
確認停止
```

`onDismissRequest` must call `onCancelStop`.

### 5.2 Render buttons and shared enabled rule

Import `ButtonDefaults` and compute:

```kotlin
val operationsControlsEnabled =
    !state.operationsLoading &&
    state.stopConfirmation == null &&
    state.stoppingTarget == null &&
    state.maintenanceConfirmation == null &&
    !state.maintenanceLoading
```

Change `JobCard` to accept `enabled`, `onSelect`, and `onStop`. Change `SessionCard` to accept `enabled` and `onStop`. Keep controls vertically stackable for 160%/200% text.

Stop button:

```kotlin
OutlinedButton(
    onClick = { onStop(job.id) },
    enabled = enabled,
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.error,
    ),
) { Text("停止 Job") }
```

Session uses `停止 Session`. Refresh and Job detail use the same enabled rule. Maintenance buttons additionally require no stop confirmation/stopping.

### 5.3 Render progress and safe feedback

```kotlin
state.stoppingTarget?.let { target ->
    if (!state.reducedMotion) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(
        when (target) {
            is OperationStopTarget.Job -> "正在停止 Job #${target.id}"
            is OperationStopTarget.Session -> "正在停止 Session #${target.id}"
        },
    )
}
state.stopMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
state.stopError?.let { error ->
    Text(error.title, color = MaterialTheme.colorScheme.error)
    error.userMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
```

Replace the current read-only sentence with:

```text
單一 Job／Session 可在二次確認後停止；不提供 Session 命令、批量停止或自動重試。
```

Do not use Toast-only feedback or display technical/raw values.

### 5.4 Build and lint

```bash
gradle --no-daemon --stacktrace \
  :feature:dashboard:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

### 5.5 Update README

Add capability/safety bullets:

```markdown
- 可在二次確認後停止單一 Job 或單一 Session
- 每次確認最多送出一次 `job.stop` 或 `session.stop`，不自動重試
- 停止成功後只執行一次 Jobs／Sessions 原子重新讀取；任一讀取失敗會保留舊快照
- 不提供 Session 命令、批量停止、全部停止、自動輪詢或背景操作
```

Add smoke checks:

```markdown
- [ ] 取消停止確認時不送出 RPC
- [ ] Job 停止成功後只自動驗證刷新一次
- [ ] Session 停止成功後只自動驗證刷新一次
- [ ] 快速連點只產生一個停止 RPC
- [ ] 停止期間刷新、詳情、其他停止與維護控制不可用
- [ ] RPC 離線時不顯示誤導性的停止成功
- [ ] 160%／200% 字體下確認內容與按鈕完整可用
- [ ] reduced-motion 下停止過程只使用靜態文字
```

### 5.6 Run the exact full existing gate

```bash
gradle --no-daemon --stacktrace \
  :app:assembleDebug \
  :app:lintDebug \
  :app:testDebugUnitTest \
  :domain:installation:testDebugUnitTest \
  :core:security:testDebugUnitTest \
  :core:termux:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :core:rpc:testDebugUnitTest \
  :core:reporting:testDebugUnitTest \
  :feature:modules:testDebugUnitTest \
  :feature:dashboard:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:reports:testDebugUnitTest \
  :feature:diagnostics:testDebugUnitTest

python3 -m pip install --disable-pip-version-check check-jsonschema
bash termux-bridge/tests/contract_test.sh
bash -n \
  termux-bridge/scripts/dispatch.sh \
  termux-bridge/scripts/lib/common.sh \
  termux-bridge/scripts/actions/*.sh

termux-bridge/packaging/build_bundle.sh
git diff --exit-code -- \
  termux-bridge/scripts \
  termux-bridge/packaging/build_bundle.sh \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt

git diff --check
git status --short
```

Expected: all gates pass; bundle rebuild creates no diff; only intended files are modified.

Commit:

```bash
git add feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt README.md
git commit -m "feat: add confirmed Job and Session stop controls"
```

### 5.7 Draft PR and CI review

Push:

```bash
git push -u origin feature/phase8a-single-operation-stop
```

Draft PR title:

```text
feat: add confirmed single Job and Session stop
```

PR body must record RED/GREEN evidence and explicitly state:

- only `job.stop` and `session.stop` were added;
- one cross-operation lock and one atomic verification refresh are enforced;
- there is no Session I/O, batch operation, retry, polling, persistence, permission, Bridge runtime, or release-signing change.

Before marking ready, require successful Bridge contract/reproducibility, Debug Build, Lint, `core:rpc`, `feature:dashboard`, all existing risk-directed tests, and Debug APK upload. Compare `main...feature/phase8a-single-operation-stop` and reject unrelated changes.
