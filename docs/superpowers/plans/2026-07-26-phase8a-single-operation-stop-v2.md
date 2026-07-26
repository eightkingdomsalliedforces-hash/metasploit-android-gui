# MAGO Phase 8A Single Job and Session Stop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fail-closed stopping of exactly one currently listed Metasploit Job or Session from the existing Dashboard, followed by one atomic verification refresh.

**Architecture:** Extend the existing `RpcOperationsService` and `MetasploitOperationsRepository`; do not create a parallel Operations feature. `DashboardViewModel` owns confirmation, global locking, one stop call, one verification refresh, and evidence-based feedback. Compose renders typed state and invokes callbacks only.

**Tech Stack:** Kotlin, Android, Jetpack Compose Material 3, ViewModel/StateFlow, Kotlin coroutines test, JUnit 4, Google Truth, MessagePack RPC, GitHub Actions.

## Global Constraints

- Create `feature/phase8a-single-operation-stop` from `design/phase8a-single-operation-stop` in a worktree created through `superpowers:using-git-worktrees`.
- Add only `job.stop` and `session.stop`.
- Every stop requires a visible second confirmation and `userConfirmed = true` at the RPC service boundary.
- Unconfirmed, invalid-ID, missing-token, missing-target, and non-READY paths perform zero stop transport calls.
- Each accepted confirmation produces at most one stop RPC; no stop retry, connection retry, refresh retry, polling, queue, or background execution.
- Keep `OkHttpRpcTransport.retryOnConnectionFailure(false)` unchanged.
- Confirmation and stopping are mutually exclusive. Either state blocks Operations refresh, Job detail, maintenance, and another stop request.
- After stop RPC success, call `jobs()` once and `sessions()` once. Apply neither list unless both reads succeed.
- A failed verification read preserves both pre-stop lists and the selected Job.
- Do not log or persist stop targets, IDs, RPC tokens, raw responses, technical messages, diagnostic data, or exception text.
- Keep the current Dashboard destination. Do not add a route, module, permission, database schema, notification, service, Session I/O, console input, Meterpreter API, batch stop, or stop-all.
- Reduced-motion mode uses static progress text.
- Do not modify signed-release behavior or secrets.

---

## File Map

- `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt` — fixed method constants.
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt` — service confirmation/ID gates, exact calls, strict response parsing.
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt` — RPC contract tests.
- `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt` — domain stop methods.
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt` — authentication gate and forwarding.
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt` — repository boundary tests.
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt` — typed stop target/error and target-presence predicate.
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt` — atomic loading and stop state machine.
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt` — UI state, dialog, buttons, progress, feedback.
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/OperationStopStateTest.kt` — target-presence predicate tests.
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt` — ViewModel state-machine tests.
- `README.md` — capability, safety, verification, smoke checks.

---

### Task 1: Add strict stop RPC contracts

**Files:**
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt`
- Modify: `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt`

**Interfaces:**
- Produces `RpcMethod.JOB_STOP` and `RpcMethod.SESSION_STOP`.
- Produces `stopJob(token: String, jobId: String, userConfirmed: Boolean): AppResult<Unit>`.
- Produces `stopSession(token: String, sessionId: Int, userConfirmed: Boolean): AppResult<Unit>`.

- [ ] **Step 1: Replace the test transport with a call-recording transport**

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

- [ ] **Step 2: Write RED tests for all pre-transport gates**

```kotlin
@Test
fun `unconfirmed job stop performs zero calls`() = runTest {
    val transport = FakeTransport(successResponse())
    val result = RpcOperationsService(transport).stopJob("token", "4", false)
    assertFailureCode(result, "RPC_JOB_CONFIRMATION_REQUIRED")
    assertThat(transport.calls).isEqualTo(0)
}

@Test
fun `invalid job IDs perform zero calls`() = runTest {
    listOf("bad", "-1", "9223372036854775808").forEach { id ->
        val transport = FakeTransport(successResponse())
        val result = RpcOperationsService(transport).stopJob("token", id, true)
        assertFailureCode(result, "RPC_JOB_ID_INVALID")
        assertThat(transport.calls).isEqualTo(0)
    }
}

@Test
fun `unconfirmed and negative session stop perform zero calls`() = runTest {
    val unconfirmed = FakeTransport(successResponse())
    assertFailureCode(
        RpcOperationsService(unconfirmed).stopSession("token", 7, false),
        "RPC_SESSION_CONFIRMATION_REQUIRED",
    )
    assertThat(unconfirmed.calls).isEqualTo(0)

    val negative = FakeTransport(successResponse())
    assertFailureCode(
        RpcOperationsService(negative).stopSession("token", -1, true),
        "RPC_SESSION_ID_INVALID",
    )
    assertThat(negative.calls).isEqualTo(0)
}
```

- [ ] **Step 3: Run RED**

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.service.RpcOperationsServiceTest'
```

Expected: compilation fails because stop methods/constants do not exist.

- [ ] **Step 4: Add exact RPC constants**

```kotlin
val JOB_STOP = RpcMethod("job.stop")
val SESSION_STOP = RpcMethod("session.stop")
```

Place them beside the existing Job/Session methods.

- [ ] **Step 5: Implement service confirmation, ID validation, exact calls, and strict result parsing**

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
            value = response.value,
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
            value = response.value,
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

- [ ] **Step 6: Add exact-call, malformed-response, and no-retry tests**

```kotlin
@Test
fun `job stop sends one integer argument and accepts case insensitive success`() = runTest {
    val transport = FakeTransport(
        RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("SuCcEsS"))),
    )
    val result = RpcOperationsService(transport).stopJob("token", "4", true)

    assertThat(result).isInstanceOf(AppResult.Success::class.java)
    assertThat(transport.calls).isEqualTo(1)
    assertThat(transport.lastMethod).isEqualTo(RpcMethod.JOB_STOP)
    assertThat(transport.lastToken).isEqualTo("token")
    assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(4))
}

@Test
fun `session stop sends one integer argument`() = runTest {
    val transport = FakeTransport(successResponse())
    val result = RpcOperationsService(transport).stopSession("token", 7, true)

    assertThat(result).isInstanceOf(AppResult.Success::class.java)
    assertThat(transport.calls).isEqualTo(1)
    assertThat(transport.lastMethod).isEqualTo(RpcMethod.SESSION_STOP)
    assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(7))
}

@Test
fun `malformed stop responses fail closed`() = runTest {
    val malformed = listOf(
        RpcValue.StringValue("success"),
        RpcValue.MapValue(emptyMap()),
        RpcValue.MapValue(mapOf("result" to RpcValue.IntValue(1))),
    )
    malformed.forEach { value ->
        assertFailureCode(
            RpcOperationsService(FakeTransport(value)).stopJob("token", "4", true),
            "RPC_JOB_STOP_RESPONSE_INVALID",
        )
        assertFailureCode(
            RpcOperationsService(FakeTransport(value)).stopSession("token", 7, true),
            "RPC_SESSION_STOP_RESPONSE_INVALID",
        )
    }
}

@Test
fun `non-success result and transport failure are not retried`() = runTest {
    val failedValue = RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("failed")))
    assertFailureCode(
        RpcOperationsService(FakeTransport(failedValue)).stopJob("token", "4", true),
        "RPC_JOB_STOP_FAILED",
    )

    val expected = AppResult.Failure(
        AppError(errorCode = "RPC_NETWORK_ERROR", userMessage = "無法連接本機 RPC"),
    )
    val transport = FakeTransport(expected)
    val result = RpcOperationsService(transport).stopSession("token", 7, true)
    assertThat(result).isSameInstanceAs(expected)
    assertThat(transport.calls).isEqualTo(1)
}
```

Add `import dev.mago.android.model.AppError`.

- [ ] **Step 7: Run GREEN and commit**

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest
git add core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt \
  core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt \
  core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt
git commit -m "feat: add confirmed single operation stop RPC"
```

---

### Task 2: Extend the token-gated repository

**Files:**
- Modify: `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt`
- Create: `core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt`

**Interfaces:**
- Produces `stopJob(jobId: String, userConfirmed: Boolean): AppResult<Unit>`.
- Produces `stopSession(sessionId: Int, userConfirmed: Boolean): AppResult<Unit>`.

- [ ] **Step 1: Create RED repository tests**

```kotlin
package dev.mago.android.rpc

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.security.RpcTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MetasploitOperationsRepositoryImplTest {
    @Test
    fun `missing token blocks stop before transport`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(transport, FakeTokenStore(null))

        val result = repository.stopJob("4", true)

        assertThat((result as AppResult.Failure).error.errorCode)
            .isEqualTo("RPC_NOT_AUTHENTICATED")
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `authenticated stops forward exactly once`() = runTest {
        val jobTransport = RecordingTransport()
        val jobRepository = MetasploitOperationsRepositoryImpl(jobTransport, FakeTokenStore("token"))
        assertThat(jobRepository.stopJob("4", true)).isInstanceOf(AppResult.Success::class.java)
        assertThat(jobTransport.calls).isEqualTo(1)
        assertThat(jobTransport.lastMethod).isEqualTo(RpcMethod.JOB_STOP)
        assertThat(jobTransport.lastArguments).containsExactly(RpcValue.IntValue(4))

        val sessionTransport = RecordingTransport()
        val sessionRepository = MetasploitOperationsRepositoryImpl(sessionTransport, FakeTokenStore("token"))
        assertThat(sessionRepository.stopSession(7, true)).isInstanceOf(AppResult.Success::class.java)
        assertThat(sessionTransport.calls).isEqualTo(1)
        assertThat(sessionTransport.lastMethod).isEqualTo(RpcMethod.SESSION_STOP)
        assertThat(sessionTransport.lastArguments).containsExactly(RpcValue.IntValue(7))
    }

    private class RecordingTransport : RpcTransport {
        var calls = 0
        var lastMethod: RpcMethod? = null
        var lastArguments: List<RpcValue> = emptyList()

        override suspend fun call(
            method: RpcMethod,
            token: String?,
            arguments: List<RpcValue>,
        ): AppResult<RpcValue> {
            calls += 1
            lastMethod = method
            lastArguments = arguments
            return AppResult.Success(
                RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("success"))),
            )
        }
    }

    private class FakeTokenStore(private val token: String?) : RpcTokenStore {
        override fun get(): String? = token
        override fun set(token: String) = Unit
        override fun clear() = Unit
    }
}
```

- [ ] **Step 2: Run RED**

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.MetasploitOperationsRepositoryImplTest'
```

Expected: stop methods do not exist.

- [ ] **Step 3: Extend the domain interface**

```kotlin
suspend fun stopJob(jobId: String, userConfirmed: Boolean): AppResult<Unit>
suspend fun stopSession(sessionId: Int, userConfirmed: Boolean): AppResult<Unit>
```

- [ ] **Step 4: Implement token-gated forwarding**

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

- [ ] **Step 5: Run GREEN and commit**

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest
git add domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt \
  core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt \
  core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt
git commit -m "feat: expose authenticated operation stop repository"
```

---

### Task 3: Add typed stop state and atomic Operations loading

**Files:**
- Create: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt`
- Create: `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/OperationStopStateTest.kt`
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt` (`DashboardUiState` only)
- Modify: `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces typed stop target/error and `existsIn` predicate.
- Produces atomic `loadOperationsSnapshot`.
- Produces `requestStopJob`, `requestStopSession`, and `cancelStop`.
- Exposes confirmation/error/request/cancel in `DashboardUiState` in this task.

- [ ] **Step 1: Create the typed model and target-presence predicate**

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

- [ ] **Step 2: Create exact predicate tests**

```kotlin
package dev.mago.android.dashboard

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import org.junit.Test

class OperationStopStateTest {
    @Test
    fun `job target requires matching current ID`() {
        val target = OperationStopTarget.Job("2", "Example Job")
        assertThat(target.existsIn(listOf(MetasploitJobSummary("2", "Example Job")), emptyList())).isTrue()
        assertThat(target.existsIn(emptyList(), emptyList())).isFalse()
    }

    @Test
    fun `session target requires matching current ID`() {
        val target = OperationStopTarget.Session(7, "Meterpreter", "exploit/multi/handler")
        assertThat(target.existsIn(emptyList(), listOf(session(7)))).isTrue()
        assertThat(target.existsIn(emptyList(), listOf(session(8)))).isFalse()
    }

    private fun session(id: Int) = MetasploitSessionSummary(
        id = id,
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
    )
}
```

- [ ] **Step 3: Replace the Dashboard test repository with this complete programmable fake**

```kotlin
private class FakeOperationsRepository : MetasploitOperationsRepository {
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

    override suspend fun jobs(): AppResult<List<MetasploitJobSummary>> =
        jobsResult.also { jobsCalls += 1 }

    override suspend fun sessions(): AppResult<List<MetasploitSessionSummary>> =
        sessionsResult.also { sessionsCalls += 1 }

    override suspend fun jobInfo(jobId: String): AppResult<MetasploitJobInfo> =
        jobInfoResult.also { jobInfoCalls += jobId }

    override suspend fun stopJob(jobId: String, userConfirmed: Boolean): AppResult<Unit> {
        stopJobCalls += jobId to userConfirmed
        stopJobGate?.await()
        return stopJobResult
    }

    override suspend fun stopSession(sessionId: Int, userConfirmed: Boolean): AppResult<Unit> {
        stopSessionCalls += sessionId to userConfirmed
        return stopSessionResult
    }

    companion object {
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
    }
}
```

Add imports:

```kotlin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
```

- [ ] **Step 4: Add RED tests for atomic refresh and zero-RPC request/cancel**

```kotlin
@Test
fun `manual refresh failure preserves both old lists`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    repository.jobsResult = AppResult.Failure(
        AppError(errorCode = "JOBS_FAILED", userMessage = "jobs failed"),
    )
    repository.sessionsResult = AppResult.Success(emptyList())
    viewModel.refreshOperations()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.jobs).containsExactlyElementsIn(FakeOperationsRepository.defaultJobs())
    assertThat(viewModel.uiState.value.sessions).containsExactlyElementsIn(FakeOperationsRepository.defaultSessions())
    collection.cancel()
}

@Test
fun `request and cancel perform zero stop calls`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopJob("2")
    assertThat(viewModel.uiState.value.stopConfirmation)
        .isEqualTo(OperationStopTarget.Job("2", "Example Job"))
    assertThat(repository.stopJobCalls).isEmpty()

    viewModel.cancelStop()
    assertThat(viewModel.uiState.value.stopConfirmation).isNull()
    assertThat(repository.stopJobCalls).isEmpty()
    collection.cancel()
}

@Test
fun `missing target fails before confirmation`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopSession(99)

    assertThat(viewModel.uiState.value.stopConfirmation).isNull()
    assertThat(viewModel.uiState.value.stopError?.title).contains("已不在目前列表")
    assertThat(repository.stopSessionCalls).isEmpty()
    collection.cancel()
}
```

- [ ] **Step 5: Run RED**

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
```

Expected: missing state/methods and current partial-refresh behavior fails.

- [ ] **Step 6: Add atomic loader types and snapshot fields**

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

- [ ] **Step 7: Replace `refreshOperations` with atomic application**

```kotlin
fun refreshOperations() {
    val snapshot = operations.value
    if (
        snapshot.loading || snapshot.stopConfirmation != null ||
        snapshot.stoppingTarget != null || maintenance.value.loading
    ) return
    operations.value = snapshot.copy(
        loading = true,
        error = null,
        stopMessage = null,
        stopError = null,
    )
    viewModelScope.launch {
        when (val result = loadOperationsSnapshot()) {
            is OperationsLoadResult.Failure -> operations.value = operations.value.copy(
                loading = false,
                error = result.userMessage,
            )
            is OperationsLoadResult.Success -> operations.value = operations.value.copy(
                jobs = result.jobs,
                sessions = result.sessions,
                selectedJob = null,
                loading = false,
                error = null,
            )
        }
    }
}
```

- [ ] **Step 8: Add request/cancel methods**

```kotlin
fun requestStopJob(jobId: String) {
    val snapshot = operations.value
    if (snapshot.loading || snapshot.stopConfirmation != null || snapshot.stoppingTarget != null || maintenance.value.loading) return
    val job = snapshot.jobs.firstOrNull { it.id == jobId }
    operations.value = if (job == null) {
        snapshot.copy(
            stopMessage = null,
            stopError = OperationStopError("此 Job 已不在目前列表中，請重新整理。", null),
        )
    } else {
        snapshot.copy(
            stopConfirmation = OperationStopTarget.Job(job.id, job.name),
            stopMessage = null,
            stopError = null,
        )
    }
}

fun requestStopSession(sessionId: Int) {
    val snapshot = operations.value
    if (snapshot.loading || snapshot.stopConfirmation != null || snapshot.stoppingTarget != null || maintenance.value.loading) return
    val session = snapshot.sessions.firstOrNull { it.id == sessionId }
    operations.value = if (session == null) {
        snapshot.copy(
            stopMessage = null,
            stopError = OperationStopError("此 Session 已不在目前列表中，請重新整理。", null),
        )
    } else {
        snapshot.copy(
            stopConfirmation = OperationStopTarget.Session(
                session.id,
                session.description,
                session.viaExploit,
            ),
            stopMessage = null,
            stopError = null,
        )
    }
}

fun cancelStop() {
    if (operations.value.stoppingTarget != null) return
    operations.value = operations.value.copy(stopConfirmation = null)
}
```

- [ ] **Step 9: Expose Task 3 state/callbacks now**

Add to `DashboardUiState`:

```kotlin
val stopConfirmation: OperationStopTarget? = null,
val stopMessage: String? = null,
val stopError: OperationStopError? = null,
val onRequestStopJob: (String) -> Unit = {},
val onRequestStopSession: (Int) -> Unit = {},
val onCancelStop: () -> Unit = {},
```

Wire these fields in the combined `uiState` and `initialValue`. Guard `selectJob`, `requestMaintenance`, and `confirmMaintenance` while `stopConfirmation != null` or `stoppingTarget != null`.

- [ ] **Step 10: Run GREEN and commit**

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

### Task 4: Implement confirmed stopping and global locking

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt` (`DashboardUiState` only)
- Modify: `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces `confirmStop()`.
- Exposes `stoppingTarget` and `onConfirmStop`.

- [ ] **Step 1: Add RED tests for pre-RPC failure, one call, verification, and stop failure**

```kotlin
@Test
fun `non-ready confirmation performs zero stop calls`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.CHECKING_DEVICE), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopJob("2")
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(repository.stopJobCalls).isEmpty()
    assertThat(viewModel.uiState.value.stopError?.title).contains("尚未就緒")
    collection.cancel()
}

@Test
fun `successful job stop calls once and verifies both lists once`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()
    val jobsCalls = repository.jobsCalls
    val sessionsCalls = repository.sessionsCalls

    repository.jobsResult = AppResult.Success(emptyList())
    viewModel.requestStopJob("2")
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(repository.stopJobCalls).containsExactly("2" to true)
    assertThat(repository.jobsCalls).isEqualTo(jobsCalls + 1)
    assertThat(repository.sessionsCalls).isEqualTo(sessionsCalls + 1)
    assertThat(viewModel.uiState.value.stopMessage).isEqualTo("Job #2 已停止")
    assertThat(viewModel.uiState.value.stoppingTarget).isNull()
    collection.cancel()
}

@Test
fun `stop failure performs zero verification reads`() = runTest {
    val repository = FakeOperationsRepository().apply {
        stopSessionResult = AppResult.Failure(
            AppError(errorCode = "STOP_FAILED", userMessage = "stop failed"),
        )
    }
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()
    val jobsCalls = repository.jobsCalls
    val sessionsCalls = repository.sessionsCalls

    viewModel.requestStopSession(7)
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(repository.stopSessionCalls).containsExactly(7 to true)
    assertThat(repository.jobsCalls).isEqualTo(jobsCalls)
    assertThat(repository.sessionsCalls).isEqualTo(sessionsCalls)
    assertThat(viewModel.uiState.value.stopError?.title).isEqualTo("無法停止 Session #7")
    assertThat(viewModel.uiState.value.stoppingTarget).isNull()
    collection.cancel()
}
```

- [ ] **Step 2: Add RED tests for atomic verification and evidence-based messages**

```kotlin
@Test
fun `verification failure preserves both old lists and selected job`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()
    viewModel.selectJob("2")
    advanceUntilIdle()

    repository.jobsResult = AppResult.Success(emptyList())
    repository.sessionsResult = AppResult.Failure(
        AppError(errorCode = "SESSIONS_FAILED", userMessage = "sessions failed"),
    )
    viewModel.requestStopJob("2")
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.jobs).containsExactlyElementsIn(FakeOperationsRepository.defaultJobs())
    assertThat(viewModel.uiState.value.sessions).containsExactlyElementsIn(FakeOperationsRepository.defaultSessions())
    assertThat(viewModel.uiState.value.selectedJob?.id).isEqualTo("2")
    assertThat(viewModel.uiState.value.stopMessage)
        .isEqualTo("停止要求已成功送出，但無法確認最新狀態。請手動重新整理。")
    collection.cancel()
}

@Test
fun `still-present target is reported without claiming completion`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopSession(7)
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.stopMessage)
        .isEqualTo("停止要求已成功送出，但該項目仍出現在最新列表中。")
    collection.cancel()
}
```

- [ ] **Step 3: Add the complete global-lock RED test**

```kotlin
@Test
fun `active stop blocks second stop refresh detail and maintenance`() = runTest {
    val repository = FakeOperationsRepository()
    val gate = CompletableDeferred<Unit>()
    repository.stopJobGate = gate
    val gateway = FakeTermuxGateway()
    val viewModel = DashboardViewModel(
        FakeCoordinator(InstallationStage.READY),
        repository,
        gateway,
    )
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()
    val jobsCalls = repository.jobsCalls
    val sessionsCalls = repository.sessionsCalls

    viewModel.requestStopJob("2")
    viewModel.confirmStop()
    runCurrent()
    assertThat(viewModel.uiState.value.stoppingTarget)
        .isEqualTo(OperationStopTarget.Job("2", "Example Job"))

    viewModel.requestStopSession(7)
    viewModel.refreshOperations()
    viewModel.selectJob("2")
    viewModel.requestMaintenance(MaintenanceAction.CLEAN_CACHE)
    viewModel.confirmMaintenance()
    runCurrent()

    assertThat(repository.stopJobCalls).containsExactly("2" to true)
    assertThat(repository.stopSessionCalls).isEmpty()
    assertThat(repository.jobsCalls).isEqualTo(jobsCalls)
    assertThat(repository.sessionsCalls).isEqualTo(sessionsCalls)
    assertThat(repository.jobInfoCalls).isEmpty()
    assertThat(viewModel.uiState.value.maintenanceConfirmation).isNull()
    assertThat(gateway.actions).isEmpty()

    gate.complete(Unit)
    advanceUntilIdle()
    assertThat(viewModel.uiState.value.stoppingTarget).isNull()
    collection.cancel()
}
```

- [ ] **Step 4: Run RED**

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
```

Expected: `confirmStop` and public stopping state are absent.

- [ ] **Step 5: Expose stopping state and confirmation callback**

Add to `DashboardUiState` and wire in both state constructors:

```kotlin
val stoppingTarget: OperationStopTarget? = null,
val onConfirmStop: () -> Unit = {},
```

- [ ] **Step 6: Implement `confirmStop` with a defensive current-list recheck**

```kotlin
fun confirmStop() {
    val snapshot = operations.value
    val target = snapshot.stopConfirmation ?: return
    if (snapshot.loading || snapshot.stoppingTarget != null || maintenance.value.loading) return
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

- [ ] **Step 7: Implement one atomic post-stop verification**

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

Every terminal branch clears `stoppingTarget`; none retries.

- [ ] **Step 8: Complete global guards**

`refreshOperations`, `selectJob`, `requestMaintenance`, `confirmMaintenance`, `requestStopJob`, and `requestStopSession` return immediately while confirmation or stopping is active. No ignored action is replayed.

- [ ] **Step 9: Add selected-Job retention tests**

Add two tests:

```kotlin
@Test
fun `stopping selected job clears detail after successful refresh`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()
    viewModel.selectJob("2")
    advanceUntilIdle()

    repository.jobsResult = AppResult.Success(emptyList())
    viewModel.requestStopJob("2")
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.selectedJob).isNull()
    collection.cancel()
}

@Test
fun `stopping session retains unrelated selected job when job remains`() = runTest {
    val repository = FakeOperationsRepository().apply {
        sessionsResult = AppResult.Success(emptyList())
    }
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()
    viewModel.selectJob("2")
    advanceUntilIdle()

    viewModel.requestStopSession(7)
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.selectedJob?.id).isEqualTo("2")
    collection.cancel()
}
```

- [ ] **Step 10: Run GREEN and commit**

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
git add feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt \
  feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt
git commit -m "feat: enforce single operation stop state machine"
```

---

### Task 5: Render stop controls, document safety, and verify the branch

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt`
- Modify: `README.md`

- [ ] **Step 1: Render the confirmation dialog**

Add after the maintenance dialog:

```kotlin
state.stopConfirmation?.let { target ->
    AlertDialog(
        onDismissRequest = state.onCancelStop,
        title = {
            Text(
                when (target) {
                    is OperationStopTarget.Job -> "確認停止 Job #${target.id}？"
                    is OperationStopTarget.Session -> "確認停止 Session #${target.id}？"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (target) {
                    is OperationStopTarget.Job -> {
                        Text("名稱：${target.name}")
                        Text("停止後無法由 MAGO 復原。操作只會送出一次，不會自動重試。")
                    }
                    is OperationStopTarget.Session -> {
                        Text("來源模組：${target.sourceModule ?: "尚未取得"}")
                        Text("描述：${target.description.ifBlank { "尚未取得" }}")
                        Text("停止後此 Session 可能無法再次連線。操作只會送出一次，不會自動重試。")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = state.onConfirmStop) { Text("確認停止") }
        },
        dismissButton = {
            TextButton(onClick = state.onCancelStop) { Text("取消") }
        },
    )
}
```

- [ ] **Step 2: Add text-labeled destructive buttons and shared enabled state**

Import `ButtonDefaults`. Compute in the Jobs/Sessions section:

```kotlin
val operationsControlsEnabled =
    !state.operationsLoading &&
    state.stopConfirmation == null &&
    state.stoppingTarget == null &&
    !state.maintenanceLoading
```

Change `JobCard` to receive `enabled`, `onSelect`, and `onStop`; change `SessionCard` to receive `enabled` and `onStop`. Keep controls vertical for large text. Stop button example:

```kotlin
OutlinedButton(
    onClick = { onStop(job.id) },
    enabled = enabled,
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.error,
    ),
) { Text("停止 Job") }
```

Session uses `停止 Session`. Refresh/detail use the same enabled value. Maintenance buttons additionally require `state.stopConfirmation == null && state.stoppingTarget == null`.

- [ ] **Step 3: Render progress and evidence-based feedback**

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

Replace the old read-only sentence with:

```kotlin
Text("單一 Job／Session 可在二次確認後停止；不提供 Session 命令、批量停止或自動重試。")
```

No Toast and no technical/raw values.

- [ ] **Step 4: Build and lint before documentation changes**

```bash
gradle --no-daemon --stacktrace \
  :feature:dashboard:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

Expected: PASS.

- [ ] **Step 5: Update README capability and smoke checks**

Add:

```markdown
- 可在二次確認後停止單一 Job 或單一 Session
- 每次確認最多送出一次 `job.stop` 或 `session.stop`，不自動重試
- 停止成功後只執行一次 Jobs／Sessions 原子重新讀取；任一讀取失敗會保留舊快照
- 不提供 Session 命令、批量停止、全部停止、自動輪詢或背景操作
```

Add:

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

- [ ] **Step 6: Run the exact full existing gate**

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

Expected: all gates PASS; bundle rebuild produces no diff; only intended files are modified.

- [ ] **Step 7: Commit UI/docs**

```bash
git add feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt README.md
git commit -m "feat: add confirmed Job and Session stop controls"
```

- [ ] **Step 8: Push and open a Draft PR**

```bash
git push -u origin feature/phase8a-single-operation-stop
```

Draft PR title:

```text
feat: add confirmed single Job and Session stop
```

PR body records RED/GREEN evidence and states that only `job.stop`, `session.stop`, one global lock, and one atomic verification refresh were added. It explicitly states no Session I/O, batch operation, retry, polling, persistence, permission, Bridge runtime, or release-signing change.

- [ ] **Step 9: Verify CI and scope before marking ready**

Require successful Bridge contract/reproducibility, Debug Build, Lint, `core:rpc`, `feature:dashboard`, all existing risk-directed tests, and Debug APK upload. Compare `main...feature/phase8a-single-operation-stop`; do not mark ready or merge if any unrelated file changed.
