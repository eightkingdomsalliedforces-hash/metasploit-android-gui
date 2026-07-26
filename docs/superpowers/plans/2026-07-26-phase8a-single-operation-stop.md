# MAGO Phase 8A Single Job and Session Stop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit, fail-closed stopping of exactly one currently listed Metasploit Job or Session from the existing Dashboard, followed by one atomic verification refresh.

**Architecture:** Extend the existing `RpcOperationsService` and `MetasploitOperationsRepository` instead of creating a second Operations stack. Keep stop target, confirmation, global operation lock, atomic refresh, and safe feedback in `DashboardViewModel`; Compose only renders typed state and invokes callbacks. The RPC service independently validates confirmation and identifiers before transport.

**Tech Stack:** Kotlin, Android, Jetpack Compose Material 3, ViewModel/StateFlow, Kotlin coroutines test, JUnit 4, Google Truth, MessagePack RPC, GitHub Actions.

## Global Constraints

- Start implementation from `design/phase8a-single-operation-stop` so the approved spec and this plan remain in the feature branch.
- Use branch `feature/phase8a-single-operation-stop` in an isolated worktree created through `superpowers:using-git-worktrees`.
- Support only `job.stop` and `session.stop`; do not add Session read/write, console input, Meterpreter APIs, arbitrary RPC methods, batch stop, or stop-all.
- Every stop requires a visible second confirmation and `userConfirmed = true` at the RPC service boundary.
- Invalid identifiers, missing authentication, missing target, non-READY state, and unconfirmed requests perform zero stop transport calls.
- A confirmed action produces at most one stop RPC. Do not retry the stop, verification refresh, or network connection automatically.
- Keep `OkHttpRpcTransport.retryOnConnectionFailure(false)` unchanged.
- Allow only one global stop operation at a time; confirmation and stopping states are mutually exclusive.
- While confirmation or stopping is active, block manual Operations refresh, Job detail selection, maintenance requests/confirmation, and additional stop requests.
- After stop RPC success, call `jobs()` once and `sessions()` once. Replace visible lists only when both succeed.
- A failed post-stop read preserves both pre-stop lists and the selected Job.
- Do not persist or log stop targets, RPC tokens, raw responses, technical messages, diagnostic data, or exception text.
- Keep the current Dashboard destination and vertical scrolling surface; do not add a module, route, permission, database schema, notification, service, polling, queue, or background task.
- Use static progress text when reduced motion is enabled.
- Do not create signed release artifacts or change the existing Release workflow.

---

## File Structure

- `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt`
  - Owns the fixed `JOB_STOP` and `SESSION_STOP` method constants.
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt`
  - Owns service-layer confirmation, ID validation, exact argument construction, and strict `result=success` parsing.
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt`
  - Proves zero-call gates, exact RPC shape, strict response parsing, and no retry.
- `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt`
  - Exposes typed stop operations to the Dashboard.
- `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt`
  - Applies the existing token gate and forwards one authenticated stop request.
- `core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt`
  - Proves missing-token fail-closed behavior and authenticated forwarding.
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt`
  - Owns `OperationStopTarget` and `OperationStopError`; contains no Android platform dependency.
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
  - Owns atomic Operations loading, confirmation, global lock, one stop call, one verification refresh, and safe completion feedback.
- `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt`
  - Exposes stop state/callbacks in `DashboardUiState` and renders buttons, dialog, progress, and feedback.
- `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`
  - Proves state-machine and atomic-snapshot invariants.
- `README.md`
  - Documents single-item stop controls, safety boundary, verification command, and real-device smoke checks.

---

### Task 1: Add strict Job and Session stop RPC contracts

**Files:**
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt:42-45`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt:14-156`
- Modify: `core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt:13-111`

**Interfaces:**
- Consumes: `RpcTransport.call(method: RpcMethod, token: String?, arguments: List<RpcValue>): AppResult<RpcValue>`.
- Produces:
  - `RpcMethod.JOB_STOP`
  - `RpcMethod.SESSION_STOP`
  - `RpcOperationsService.stopJob(token: String, jobId: String, userConfirmed: Boolean): AppResult<Unit>`
  - `RpcOperationsService.stopSession(token: String, sessionId: Int, userConfirmed: Boolean): AppResult<Unit>`

- [ ] **Step 1: Extend the test transport so tests can prove exact call count, method, token, arguments, and transport failure propagation**

Replace the existing `FakeTransport` at the bottom of `RpcOperationsServiceTest.kt` with:

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
```

- [ ] **Step 2: Write failing tests for pre-transport confirmation and identifier gates**

Add these tests to `RpcOperationsServiceTest.kt`:

```kotlin
@Test
fun `unconfirmed job stop performs zero transport calls`() = runTest {
    val transport = FakeTransport(successResponse())
    val result = RpcOperationsService(transport).stopJob("token", "4", userConfirmed = false)

    assertFailureCode(result, "RPC_JOB_CONFIRMATION_REQUIRED")
    assertThat(transport.calls).isEqualTo(0)
}

@Test
fun `invalid job IDs perform zero transport calls`() = runTest {
    listOf("not-a-number", "-1", "9223372036854775808").forEach { id ->
        val transport = FakeTransport(successResponse())
        val result = RpcOperationsService(transport).stopJob("token", id, userConfirmed = true)

        assertFailureCode(result, "RPC_JOB_ID_INVALID")
        assertThat(transport.calls).isEqualTo(0)
    }
}

@Test
fun `unconfirmed session stop performs zero transport calls`() = runTest {
    val transport = FakeTransport(successResponse())
    val result = RpcOperationsService(transport).stopSession("token", 7, userConfirmed = false)

    assertFailureCode(result, "RPC_SESSION_CONFIRMATION_REQUIRED")
    assertThat(transport.calls).isEqualTo(0)
}

@Test
fun `negative session ID performs zero transport calls`() = runTest {
    val transport = FakeTransport(successResponse())
    val result = RpcOperationsService(transport).stopSession("token", -1, userConfirmed = true)

    assertFailureCode(result, "RPC_SESSION_ID_INVALID")
    assertThat(transport.calls).isEqualTo(0)
}
```

Add these helpers inside the test class:

```kotlin
private fun successResponse(): RpcValue = RpcValue.MapValue(
    mapOf("result" to RpcValue.StringValue("success")),
)

private fun assertFailureCode(result: AppResult<*>, code: String) {
    assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    assertThat((result as AppResult.Failure).error.errorCode).isEqualTo(code)
}
```

- [ ] **Step 3: Run the focused tests to verify RED**

Run:

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.service.RpcOperationsServiceTest'
```

Expected: compilation fails because `stopJob`, `stopSession`, `JOB_STOP`, and `SESSION_STOP` do not exist.

- [ ] **Step 4: Add exact RPC method constants**

In `RpcMethod.kt`, add immediately after `JOB_INFO` and `SESSION_LIST`:

```kotlin
val JOB_STOP = RpcMethod("job.stop")
val SESSION_STOP = RpcMethod("session.stop")
```

- [ ] **Step 5: Implement the minimal service-layer gates and exact transport calls**

Add to `RpcOperationsService`:

```kotlin
suspend fun stopJob(
    token: String,
    jobId: String,
    userConfirmed: Boolean,
): AppResult<Unit> {
    if (!userConfirmed) {
        return invalid(
            code = "RPC_JOB_CONFIRMATION_REQUIRED",
            message = "停止 Job 需要使用者明確確認",
            retryable = false,
        )
    }
    val parsedJobId = jobId.toLongOrNull()?.takeIf { it >= 0 }
        ?: return invalid(
            code = "RPC_JOB_ID_INVALID",
            message = "Job ID 不正確",
            retryable = false,
        )
    return when (
        val result = transport.call(
            RpcMethod.JOB_STOP,
            token,
            listOf(RpcValue.IntValue(parsedJobId)),
        )
    ) {
        is AppResult.Failure -> result
        is AppResult.Success -> parseStopResult(
            value = result.value,
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
        return invalid(
            code = "RPC_SESSION_CONFIRMATION_REQUIRED",
            message = "停止 Session 需要使用者明確確認",
            retryable = false,
        )
    }
    if (sessionId < 0) {
        return invalid(
            code = "RPC_SESSION_ID_INVALID",
            message = "Session ID 不正確",
            retryable = false,
        )
    }
    return when (
        val result = transport.call(
            RpcMethod.SESSION_STOP,
            token,
            listOf(RpcValue.IntValue(sessionId.toLong())),
        )
    ) {
        is AppResult.Failure -> result
        is AppResult.Success -> parseStopResult(
            value = result.value,
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

- [ ] **Step 6: Add failing-closed response and exact-request tests**

Add:

```kotlin
@Test
fun `job stop sends one integer argument and accepts case insensitive success`() = runTest {
    val transport = FakeTransport(
        RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("SuCcEsS"))),
    )

    val result = RpcOperationsService(transport).stopJob("token", "4", userConfirmed = true)

    assertThat(result).isInstanceOf(AppResult.Success::class.java)
    assertThat(transport.calls).isEqualTo(1)
    assertThat(transport.lastMethod).isEqualTo(RpcMethod.JOB_STOP)
    assertThat(transport.lastToken).isEqualTo("token")
    assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(4))
}

@Test
fun `session stop sends one integer argument`() = runTest {
    val transport = FakeTransport(successResponse())

    val result = RpcOperationsService(transport).stopSession("token", 7, userConfirmed = true)

    assertThat(result).isInstanceOf(AppResult.Success::class.java)
    assertThat(transport.calls).isEqualTo(1)
    assertThat(transport.lastMethod).isEqualTo(RpcMethod.SESSION_STOP)
    assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(7))
}

@Test
fun `malformed stop responses fail closed`() = runTest {
    val invalidResponses = listOf(
        RpcValue.StringValue("success"),
        RpcValue.MapValue(emptyMap()),
        RpcValue.MapValue(mapOf("result" to RpcValue.IntValue(1))),
    )
    invalidResponses.forEach { response ->
        val jobResult = RpcOperationsService(FakeTransport(response))
            .stopJob("token", "4", userConfirmed = true)
        val sessionResult = RpcOperationsService(FakeTransport(response))
            .stopSession("token", 7, userConfirmed = true)

        assertFailureCode(jobResult, "RPC_JOB_STOP_RESPONSE_INVALID")
        assertFailureCode(sessionResult, "RPC_SESSION_STOP_RESPONSE_INVALID")
    }
}

@Test
fun `non-success stop result uses operation-specific failure code`() = runTest {
    val response = RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("failed")))

    assertFailureCode(
        RpcOperationsService(FakeTransport(response)).stopJob("token", "4", true),
        "RPC_JOB_STOP_FAILED",
    )
    assertFailureCode(
        RpcOperationsService(FakeTransport(response)).stopSession("token", 7, true),
        "RPC_SESSION_STOP_FAILED",
    )
}

@Test
fun `transport failure is propagated without retry`() = runTest {
    val expected = AppResult.Failure(
        dev.mago.android.model.AppError(
            errorCode = "RPC_NETWORK_ERROR",
            userMessage = "無法連接本機 RPC",
        ),
    )
    val transport = FakeTransport(expected)

    val result = RpcOperationsService(transport).stopJob("token", "4", true)

    assertThat(result).isSameInstanceAs(expected)
    assertThat(transport.calls).isEqualTo(1)
}
```

- [ ] **Step 7: Run the focused RPC tests and the whole core RPC suite**

Run:

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.service.RpcOperationsServiceTest'
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```bash
git add \
  core/rpc/src/main/kotlin/dev/mago/android/rpc/RpcMethod.kt \
  core/rpc/src/main/kotlin/dev/mago/android/rpc/service/RpcOperationsService.kt \
  core/rpc/src/test/kotlin/dev/mago/android/rpc/service/RpcOperationsServiceTest.kt
git commit -m "feat: add confirmed single operation stop RPC"
```

---

### Task 2: Extend the authenticated Operations repository

**Files:**
- Modify: `domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt:10-14`
- Modify: `core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt:14-41`
- Create: `core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt`

**Interfaces:**
- Consumes:
  - `RpcOperationsService.stopJob(String, String, Boolean): AppResult<Unit>`
  - `RpcOperationsService.stopSession(String, Int, Boolean): AppResult<Unit>`
  - `RpcTokenStore.get(): String?`
- Produces:
  - `MetasploitOperationsRepository.stopJob(jobId: String, userConfirmed: Boolean): AppResult<Unit>`
  - `MetasploitOperationsRepository.stopSession(sessionId: Int, userConfirmed: Boolean): AppResult<Unit>`

- [ ] **Step 1: Write the repository tests before changing the interface**

Create `MetasploitOperationsRepositoryImplTest.kt`:

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
    fun `missing token blocks job stop before transport`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(transport, FakeTokenStore(null))

        val result = repository.stopJob("4", userConfirmed = true)

        assertFailureCode(result, "RPC_NOT_AUTHENTICATED")
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `authenticated job stop forwards once`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(transport, FakeTokenStore("token"))

        val result = repository.stopJob("4", userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(transport.calls).isEqualTo(1)
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.JOB_STOP)
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(4))
    }

    @Test
    fun `authenticated session stop forwards once`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(transport, FakeTokenStore("token"))

        val result = repository.stopSession(7, userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(transport.calls).isEqualTo(1)
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.SESSION_STOP)
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(7))
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

    private fun assertFailureCode(result: AppResult<*>, code: String) {
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.errorCode).isEqualTo(code)
    }
}
```

Use the exact `RpcTokenStore` method signatures already declared in `core/security`; if its write method is named differently, preserve the existing interface names while keeping the fake behavior above: return the constructor token and make writes no-ops.

- [ ] **Step 2: Run the repository test to verify RED**

Run:

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.MetasploitOperationsRepositoryImplTest'
```

Expected: compilation fails because repository stop methods do not exist.

- [ ] **Step 3: Extend the domain repository interface**

Add to `MetasploitOperationsRepository`:

```kotlin
suspend fun stopJob(
    jobId: String,
    userConfirmed: Boolean,
): AppResult<Unit>

suspend fun stopSession(
    sessionId: Int,
    userConfirmed: Boolean,
): AppResult<Unit>
```

- [ ] **Step 4: Add authenticated forwarding in the implementation**

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

Do not add logging, persistence, exception wrapping, retry, or ID normalization in the repository.

- [ ] **Step 5: Run repository and whole core RPC tests**

```bash
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest \
  --tests 'dev.mago.android.rpc.MetasploitOperationsRepositoryImplTest'
gradle --no-daemon --stacktrace :core:rpc:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add \
  domain/metasploit/src/main/kotlin/dev/mago/android/metasploit/MetasploitOperationsRepository.kt \
  core/rpc/src/main/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImpl.kt \
  core/rpc/src/test/kotlin/dev/mago/android/rpc/MetasploitOperationsRepositoryImplTest.kt
git commit -m "feat: expose authenticated operation stop repository"
```

---

### Task 3: Make Dashboard Operations loading atomic and add typed stop request state

**Files:**
- Create: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt`
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt:46-199`
- Modify: `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt:49-192`

**Interfaces:**
- Consumes: extended `MetasploitOperationsRepository` from Task 2.
- Produces:
  - `OperationStopTarget.Job(id: String, name: String)`
  - `OperationStopTarget.Session(id: Int, description: String, sourceModule: String?)`
  - `OperationStopError(title: String, userMessage: String?)`
  - atomic `loadOperationsSnapshot()` used by initial and manual refresh
  - `requestStopJob(String)`, `requestStopSession(Int)`, `cancelStop()`

- [ ] **Step 1: Create the typed stop-state model**

Create `OperationStopState.kt`:

```kotlin
package dev.mago.android.dashboard

sealed interface OperationStopTarget {
    val displayId: String

    data class Job(
        val id: String,
        val name: String,
    ) : OperationStopTarget {
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
```

- [ ] **Step 2: Extend the test repository into a programmable fake**

Replace `FakeOperationsRepository` in `DashboardViewModelTest.kt` with a fake that retains the current default data and adds programmable results and stop call recording:

```kotlin
private class FakeOperationsRepository : MetasploitOperationsRepository {
    var jobsResult: AppResult<List<MetasploitJobSummary>> = AppResult.Success(defaultJobs())
    var sessionsResult: AppResult<List<MetasploitSessionSummary>> = AppResult.Success(defaultSessions())
    var jobInfoResult: AppResult<MetasploitJobInfo> = AppResult.Success(defaultJobInfo("2"))
    var stopJobResult: AppResult<Unit> = AppResult.Success(Unit)
    var stopSessionResult: AppResult<Unit> = AppResult.Success(Unit)

    var jobsCalls = 0
    var sessionsCalls = 0
    val jobInfoCalls = mutableListOf<String>()
    val stopJobCalls = mutableListOf<Pair<String, Boolean>>()
    val stopSessionCalls = mutableListOf<Pair<Int, Boolean>>()

    override suspend fun jobs(): AppResult<List<MetasploitJobSummary>> {
        jobsCalls += 1
        return jobsResult
    }

    override suspend fun jobInfo(jobId: String): AppResult<MetasploitJobInfo> {
        jobInfoCalls += jobId
        return jobInfoResult
    }

    override suspend fun sessions(): AppResult<List<MetasploitSessionSummary>> {
        sessionsCalls += 1
        return sessionsResult
    }

    override suspend fun stopJob(jobId: String, userConfirmed: Boolean): AppResult<Unit> {
        stopJobCalls += jobId to userConfirmed
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

- [ ] **Step 3: Write failing atomic-refresh and request/cancel tests**

Add tests:

```kotlin
@Test
fun `manual refresh failure preserves previous complete snapshot`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    repository.jobsResult = AppResult.Failure(AppError("JOBS_FAILED", "jobs failed"))
    repository.sessionsResult = AppResult.Success(emptyList())
    viewModel.refreshOperations()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.jobs).containsExactlyElementsIn(FakeOperationsRepository.defaultJobs())
    assertThat(viewModel.uiState.value.sessions).containsExactlyElementsIn(FakeOperationsRepository.defaultSessions())
    assertThat(viewModel.uiState.value.operationsError).contains("jobs failed")
    collection.cancel()
}

@Test
fun `stop request opens typed confirmation and cancel performs zero stop calls`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopJob("2")
    assertThat(viewModel.uiState.value.stopConfirmation).isEqualTo(
        OperationStopTarget.Job("2", "Example Job"),
    )
    assertThat(repository.stopJobCalls).isEmpty()

    viewModel.cancelStop()
    assertThat(viewModel.uiState.value.stopConfirmation).isNull()
    assertThat(repository.stopJobCalls).isEmpty()
    collection.cancel()
}

@Test
fun `missing stop target does not open confirmation`() = runTest {
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

Construct `AppError` with named arguments if the project constructor does not accept positional arguments:

```kotlin
AppError(errorCode = "JOBS_FAILED", userMessage = "jobs failed")
```

- [ ] **Step 4: Run Dashboard tests to verify RED**

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest \
  --tests 'dev.mago.android.dashboard.DashboardViewModelTest'
```

Expected: compilation fails because stop state and request/cancel methods are absent; atomic refresh test fails under the current partial-update implementation.

- [ ] **Step 5: Add stop fields to the private Operations snapshot and define the shared loader**

In `DashboardViewModel.kt`, extend `OperationsSnapshot`:

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

- [ ] **Step 6: Replace manual refresh with atomic snapshot application**

Replace `refreshOperations()` with:

```kotlin
fun refreshOperations() {
    val snapshot = operations.value
    if (
        snapshot.loading ||
        snapshot.stoppingTarget != null ||
        snapshot.stopConfirmation != null ||
        maintenance.value.loading
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

- [ ] **Step 7: Add request and cancel methods with zero RPC behavior**

Add:

```kotlin
fun requestStopJob(jobId: String) {
    val snapshot = operations.value
    if (
        snapshot.loading ||
        snapshot.stopConfirmation != null ||
        snapshot.stoppingTarget != null ||
        maintenance.value.loading
    ) return
    val job = snapshot.jobs.firstOrNull { it.id == jobId }
    if (job == null) {
        operations.value = snapshot.copy(
            stopMessage = null,
            stopError = OperationStopError("此 Job 已不在目前列表中，請重新整理。", null),
        )
        return
    }
    operations.value = snapshot.copy(
        stopConfirmation = OperationStopTarget.Job(job.id, job.name),
        stopMessage = null,
        stopError = null,
    )
}

fun requestStopSession(sessionId: Int) {
    val snapshot = operations.value
    if (
        snapshot.loading ||
        snapshot.stopConfirmation != null ||
        snapshot.stoppingTarget != null ||
        maintenance.value.loading
    ) return
    val session = snapshot.sessions.firstOrNull { it.id == sessionId }
    if (session == null) {
        operations.value = snapshot.copy(
            stopMessage = null,
            stopError = OperationStopError("此 Session 已不在目前列表中，請重新整理。", null),
        )
        return
    }
    operations.value = snapshot.copy(
        stopConfirmation = OperationStopTarget.Session(
            id = session.id,
            description = session.description,
            sourceModule = session.viaExploit,
        ),
        stopMessage = null,
        stopError = null,
    )
}

fun cancelStop() {
    if (operations.value.stoppingTarget != null) return
    operations.value = operations.value.copy(stopConfirmation = null)
}
```

Also guard `selectJob`, `requestMaintenance`, and `confirmMaintenance` against non-null `stopConfirmation` or `stoppingTarget`; do not change maintenance execution semantics.

- [ ] **Step 8: Run Dashboard tests**

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
```

Expected: PASS for existing tests and new atomic/request/cancel tests.

- [ ] **Step 9: Commit Task 3**

```bash
git add \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/OperationStopState.kt \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt \
  feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt
git commit -m "feat: add atomic dashboard operation stop state"
```

---

### Task 4: Implement the confirmed stop state machine and verification refresh

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt`
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt:35-72`
- Modify: `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: typed stop state and repository stop methods.
- Produces:
  - `confirmStop()`
  - complete `DashboardUiState` stop properties and callbacks
  - globally locked stop lifecycle with exact completion messages

- [ ] **Step 1: Write failing pre-RPC and single-call tests**

Add:

```kotlin
@Test
fun `confirm stop before READY performs zero stop calls`() = runTest {
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
fun `confirmed job stop calls once and refreshes both lists once`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()
    val initialJobsCalls = repository.jobsCalls
    val initialSessionsCalls = repository.sessionsCalls

    viewModel.requestStopJob("2")
    repository.jobsResult = AppResult.Success(emptyList())
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(repository.stopJobCalls).containsExactly("2" to true)
    assertThat(repository.stopSessionCalls).isEmpty()
    assertThat(repository.jobsCalls).isEqualTo(initialJobsCalls + 1)
    assertThat(repository.sessionsCalls).isEqualTo(initialSessionsCalls + 1)
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
    assertThat(viewModel.uiState.value.stopError?.userMessage).isEqualTo("stop failed")
    assertThat(viewModel.uiState.value.stoppingTarget).isNull()
    collection.cancel()
}
```

- [ ] **Step 2: Write failing post-stop consistency and selected-Job tests**

Add:

```kotlin
@Test
fun `post-stop refresh failure preserves complete old snapshot`() = runTest {
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
fun `target still present after verification is reported without claiming stopped`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(FakeCoordinator(InstallationStage.READY), repository, FakeTermuxGateway())
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopSession(7)
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.sessions.single().id).isEqualTo(7)
    assertThat(viewModel.uiState.value.stopMessage)
        .isEqualTo("停止要求已成功送出，但該項目仍出現在最新列表中。")
    collection.cancel()
}

@Test
fun `stopped selected job detail is cleared after successful refresh`() = runTest {
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
```

- [ ] **Step 3: Run the focused tests to verify RED**

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest \
  --tests 'dev.mago.android.dashboard.DashboardViewModelTest'
```

Expected: compilation fails because `confirmStop` and public stop state are absent.

- [ ] **Step 4: Expose stop state and callbacks through DashboardUiState**

Add to `DashboardUiState`:

```kotlin
val stopConfirmation: OperationStopTarget? = null,
val stoppingTarget: OperationStopTarget? = null,
val stopMessage: String? = null,
val stopError: OperationStopError? = null,
val onRequestStopJob: (String) -> Unit = {},
val onRequestStopSession: (Int) -> Unit = {},
val onConfirmStop: () -> Unit = {},
val onCancelStop: () -> Unit = {},
```

In both `uiState` mapping and `initialValue`, wire exact ViewModel values and method references.

- [ ] **Step 5: Implement `confirmStop()` with one stop call and one shared verification load**

Add to `DashboardViewModel`:

```kotlin
fun confirmStop() {
    val snapshot = operations.value
    val target = snapshot.stopConfirmation ?: return
    if (
        snapshot.loading ||
        snapshot.stoppingTarget != null ||
        maintenance.value.loading
    ) return
    if (coordinator.state.value.stage != InstallationStage.READY) {
        operations.value = snapshot.copy(
            stopConfirmation = null,
            stopError = OperationStopError("RPC 環境尚未就緒，未送出停止要求。", null),
        )
        return
    }
    if (!targetStillExists(target, snapshot)) {
        operations.value = snapshot.copy(
            stopConfirmation = null,
            stopError = missingTargetError(target),
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
        val stopResult = when (target) {
            is OperationStopTarget.Job -> operationsRepository.stopJob(target.id, true)
            is OperationStopTarget.Session -> operationsRepository.stopSession(target.id, true)
        }
        when (stopResult) {
            is AppResult.Failure -> operations.value = operations.value.copy(
                stoppingTarget = null,
                stopError = OperationStopError(
                    title = when (target) {
                        is OperationStopTarget.Job -> "無法停止 Job #${target.id}"
                        is OperationStopTarget.Session -> "無法停止 Session #${target.id}"
                    },
                    userMessage = stopResult.error.userMessage,
                ),
            )
            is AppResult.Success -> applyPostStopRefresh(target)
        }
    }
}

private fun targetStillExists(
    target: OperationStopTarget,
    snapshot: OperationsSnapshot,
): Boolean = when (target) {
    is OperationStopTarget.Job -> snapshot.jobs.any { it.id == target.id }
    is OperationStopTarget.Session -> snapshot.sessions.any { it.id == target.id }
}

private fun missingTargetError(target: OperationStopTarget): OperationStopError =
    OperationStopError(
        title = when (target) {
            is OperationStopTarget.Job -> "此 Job 已不在目前列表中，請重新整理。"
            is OperationStopTarget.Session -> "此 Session 已不在目前列表中，請重新整理。"
        },
        userMessage = null,
    )
```

- [ ] **Step 6: Implement atomic post-stop refresh and evidence-based completion messages**

Add:

```kotlin
private suspend fun applyPostStopRefresh(target: OperationStopTarget) {
    when (val result = loadOperationsSnapshot()) {
        is OperationsLoadResult.Failure -> operations.value = operations.value.copy(
            stoppingTarget = null,
            stopMessage = "停止要求已成功送出，但無法確認最新狀態。請手動重新整理。",
        )
        is OperationsLoadResult.Success -> {
            val targetPresent = when (target) {
                is OperationStopTarget.Job -> result.jobs.any { it.id == target.id }
                is OperationStopTarget.Session -> result.sessions.any { it.id == target.id }
            }
            val previousSelected = operations.value.selectedJob
            val selectedJob = previousSelected?.takeIf { selected ->
                target !is OperationStopTarget.Job || selected.id != target.id
            }?.takeIf { selected -> result.jobs.any { it.id == selected.id } }
            operations.value = operations.value.copy(
                jobs = result.jobs,
                sessions = result.sessions,
                selectedJob = selectedJob,
                stoppingTarget = null,
                error = null,
                stopMessage = if (targetPresent) {
                    "停止要求已成功送出，但該項目仍出現在最新列表中。"
                } else {
                    when (target) {
                        is OperationStopTarget.Job -> "Job #${target.id} 已停止"
                        is OperationStopTarget.Session -> "Session #${target.id} 已停止"
                    }
                },
            )
        }
    }
}
```

Do not clear `stopError` in unrelated branches except when a new accepted request or manual refresh begins. Every stop terminal branch must set `stoppingTarget = null`.

- [ ] **Step 7: Complete global lock guards**

Update these methods so they return immediately while confirmation or stopping is active:

```kotlin
selectJob(...)
refreshOperations()
requestMaintenance(...)
confirmMaintenance()
```

Also block stop requests while `maintenance.value.loading` and block maintenance controls while stopping. There is no queue and no deferred replay.

- [ ] **Step 8: Add a global-lock test**

Use `CompletableDeferred<Unit>` in the fake repository to suspend `stopJob` until the test releases it. While suspended, invoke a second stop request, refresh, Job detail selection, and maintenance request; assert stop call count remains one, read/detail counts do not change, and maintenance confirmation remains null. Release the deferred, then assert `stoppingTarget` clears.

Implement this by adding optional fields to `FakeOperationsRepository`:

```kotlin
var stopJobGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
```

and awaiting it inside `stopJob` before returning. The test must call `runCurrent()` after `confirmStop()` to observe the active lock before completing the gate.

- [ ] **Step 9: Run Dashboard tests**

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 10: Commit Task 4**

```bash
git add \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardViewModel.kt \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt \
  feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt
git commit -m "feat: enforce single operation stop state machine"
```

---

### Task 5: Render the confirmation UI, document the boundary, and run the full gate

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt:74-316`
- Modify: `README.md`

**Interfaces:**
- Consumes: complete `DashboardUiState` stop fields/callbacks from Task 4.
- Produces: accessible Job/Session stop controls, confirmation dialog, static/dynamic progress, safe feedback, and updated project documentation.

- [ ] **Step 1: Render the stop confirmation dialog**

At the start of `DashboardScreen`, after the maintenance dialog, add:

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

Do not add a custom focus trap. Use normal Material dialog focus restoration.

- [ ] **Step 2: Add text-labeled destructive actions to Job and Session cards**

Change signatures:

```kotlin
private fun JobCard(
    job: MetasploitJobSummary,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onStop: (String) -> Unit,
)

private fun SessionCard(
    session: MetasploitSessionSummary,
    enabled: Boolean,
    onStop: (Int) -> Unit,
)
```

Render controls in a vertically spaced `Column` so 160% and 200% font scales remain usable:

```kotlin
OutlinedButton(
    onClick = { onSelect(job.id) },
    enabled = enabled,
) { Text("查看詳情") }

OutlinedButton(
    onClick = { onStop(job.id) },
    enabled = enabled,
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.error,
    ),
) { Text("停止 Job") }
```

For Session, add only `停止 Session` with the same error content color and enabled rule.

Calculate once in the Jobs/Sessions section:

```kotlin
val operationsControlsEnabled =
    !state.operationsLoading &&
    state.stopConfirmation == null &&
    state.stoppingTarget == null &&
    !state.maintenanceLoading
```

Use it for refresh, detail, and every stop button. Maintenance buttons must additionally require `state.stopConfirmation == null && state.stoppingTarget == null`.

- [ ] **Step 3: Render running and completion feedback without Toast**

Replace the old read-only notice with:

```kotlin
Text("單一 Job／Session 可在二次確認後停止；不提供 Session 命令、批量停止或自動重試。")
```

Add:

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

Do not render technical messages, raw RPC values, exception strings, tokens, or endpoints.

- [ ] **Step 4: Build and lint the UI before documentation changes**

```bash
gradle --no-daemon --stacktrace \
  :feature:dashboard:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug
```

Expected: PASS.

- [ ] **Step 5: Update README capabilities, safety boundary, verification, and smoke checks**

In the Jobs/Sessions capability section, document:

```markdown
- 可在二次確認後停止單一 Job 或單一 Session
- 每次確認最多送出一次 `job.stop` 或 `session.stop`，不自動重試
- 停止成功後只執行一次 Jobs／Sessions 原子重新讀取；任一讀取失敗會保留舊快照
- 不提供 Session 命令、批量停止、全部停止、自動輪詢或背景操作
```

Ensure the verification command still includes:

```text
:core:rpc:testDebugUnitTest
:feature:dashboard:testDebugUnitTest
```

Add real-device checks:

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

- [ ] **Step 6: Run the complete repository verification gate**

Run the same complete command documented in README and CI. At minimum:

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
  :feature:onboarding:testDebugUnitTest \
  :feature:terminal:testDebugUnitTest \
  :feature:modules:testDebugUnitTest \
  :feature:dashboard:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:reports:testDebugUnitTest \
  :feature:diagnostics:testDebugUnitTest

python3 -m pip install --disable-pip-version-check check-jsonschema
bash termux-bridge/tests/contract_test.sh
bash -n termux-bridge/bin/mago-bridge
bash -n termux-bridge/scripts/build_bundle.sh
```

Run the repository's existing embedded Bridge bundle checksum/reproducibility commands exactly as listed in README. Then run:

```bash
git diff --check
git status --short
```

Expected: all tests/build/lint/Bridge checks PASS; `git diff --check` produces no output; only intended files are modified.

- [ ] **Step 7: Commit Task 5**

```bash
git add \
  feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt \
  README.md
git commit -m "feat: add confirmed Job and Session stop controls"
```

- [ ] **Step 8: Push and open a Draft PR for CI evidence**

```bash
git push -u origin feature/phase8a-single-operation-stop
```

Open a Draft PR against `main` titled:

```text
feat: add confirmed single Job and Session stop
```

PR body must state:

- only `job.stop` and `session.stop` were added;
- service confirmation and identifier gates perform zero transport on failure;
- one global stop lock is enforced;
- post-stop verification reads Jobs and Sessions exactly once and applies them atomically;
- no Session interaction, batch operation, polling, retry, persistence, permission, or Release-signing change;
- focused RED/GREEN evidence and the final Android workflow run number.

Do not mark the PR ready and do not merge until the Android workflow succeeds and the patch scope is reviewed.

- [ ] **Step 9: Review CI and branch scope before declaring completion**

Verify the PR head workflow has completed successfully. Confirm:

```text
Bridge contract: success
Embedded bundle reproducibility: success
Debug Build: success
Lint: success
core:rpc tests: success
feature:dashboard tests: success
Debug APK upload: success
```

Compare `main...feature/phase8a-single-operation-stop` and confirm changes are limited to the approved spec/plan plus the files listed in this plan. No Release workflow, permission, Session I/O, persistence, database, report, inventory, terminal, module execution, or Bridge runtime file may change.
