# MAGO Phase 8A Single Job and Session Stop Design

## Goal

Add explicit, single-item lifecycle controls to the existing Dashboard Jobs and Sessions section. A user may stop exactly one currently listed Job or Session after a second confirmation. The operation is sent once, never retried automatically, and is followed by one atomic Jobs/Sessions refresh to verify the latest server state.

## Scope decisions

- Keep Jobs and Sessions in the existing Dashboard; do not add a destination or feature module.
- Support only `job.stop` and `session.stop`.
- Use a confirmation dialog that identifies the exact type and ID.
- Allow only one global stop operation at a time.
- After a successful stop RPC, call `jobs()` once and `sessions()` once.
- Replace the visible snapshot only when both reads succeed.
- Do not add Session read/write, console input, batch stop, stop-all, polling, background work, notifications, or persistent stop history.

## Existing boundary

The current `MetasploitOperationsRepository` supports read-only `jobs()`, `jobInfo()`, and `sessions()`. `DashboardViewModel` owns the passive Jobs/Sessions snapshot and the Dashboard already contains the cards and manual refresh control. `OkHttpRpcTransport` is localhost-only and configures `retryOnConnectionFailure(false)`.

Phase 8A extends those existing boundaries rather than reviving the obsolete Phase 4 PR or creating a parallel Operations architecture.

## Architecture

### Stop target model

Add the following model in the Dashboard feature package:

```kotlin
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
```

This model contains only fields already shown in the current Dashboard. It does not contain tokens, raw RPC values, endpoints, technical errors, or Session I/O.

### Repository contract

Extend `MetasploitOperationsRepository`:

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

`MetasploitOperationsRepositoryImpl` must obtain the RPC token before invoking the service. Missing authentication returns the existing `RPC_NOT_AUTHENTICATED` failure and performs zero stop-service calls. When authenticated, it forwards the exact ID and confirmation value once.

### RPC methods

Add only:

```kotlin
val JOB_STOP = RpcMethod("job.stop")
val SESSION_STOP = RpcMethod("session.stop")
```

No `session.interactive_*`, console, Meterpreter, arbitrary method, or batch method is introduced.

### RPC service contract

Extend the existing `RpcOperationsService` rather than creating separate Job and Session service stacks.

#### Job stop

```kotlin
suspend fun stopJob(
    token: String,
    jobId: String,
    userConfirmed: Boolean,
): AppResult<Unit>
```

Rules:

1. `userConfirmed` must be `true`; otherwise return `RPC_JOB_CONFIRMATION_REQUIRED` with zero transport calls.
2. `jobId` must parse completely as a non-negative `Long`; otherwise return `RPC_JOB_ID_INVALID` with zero transport calls.
3. Store the validated value as `parsedJobId` and call `job.stop` exactly once with one argument: `RpcValue.IntValue(parsedJobId)`.
4. Transport failures are returned unchanged.
5. A success response must be a map containing a string `result`.
6. `result` is accepted only when it equals `success`, ignoring case.
7. A non-map, missing result, or non-string result returns `RPC_JOB_STOP_RESPONSE_INVALID`.
8. Any other string result returns `RPC_JOB_STOP_FAILED`.

#### Session stop

```kotlin
suspend fun stopSession(
    token: String,
    sessionId: Int,
    userConfirmed: Boolean,
): AppResult<Unit>
```

Rules are identical, with these differences:

- `sessionId` must be non-negative.
- Call `session.stop` exactly once with `RpcValue.IntValue(sessionId.toLong())`.
- Fixed errors are `RPC_SESSION_CONFIRMATION_REQUIRED`, `RPC_SESSION_ID_INVALID`, `RPC_SESSION_STOP_RESPONSE_INVALID`, and `RPC_SESSION_STOP_FAILED`.

The service does not retry, poll, or infer completion from any response other than the exact success contract.

## Dashboard state

Extend the private operations snapshot:

```kotlin
private data class OperationsSnapshot(
    val jobs: List<MetasploitJobSummary> = emptyList(),
    val sessions: List<MetasploitSessionSummary> = emptyList(),
    val selectedJob: MetasploitJobInfo? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val stopConfirmation: OperationStopTarget? = null,
    val stoppingTarget: OperationStopTarget? = null,
    val stopMessage: String? = null,
    val stopError: OperationStopError? = null,
)

data class OperationStopError(
    val title: String,
    val userMessage: String?,
)
```

`stopConfirmation` and `stoppingTarget` are mutually exclusive. The state must never contain both.

Expose through `DashboardUiState`:

- `stopConfirmation`
- `stoppingTarget`
- `stopMessage`
- `stopError`
- `onRequestStopJob(String)`
- `onRequestStopSession(Int)`
- `onConfirmStop()`
- `onCancelStop()`

The ViewModel constructs `OperationStopTarget` only from the current in-memory list. The UI does not construct arbitrary stop targets.

## State machine

```text
Idle
  -> request stop -> Confirming(target)
      -> cancel -> Idle
      -> confirm
          -> not READY or target absent -> Failed without stop RPC
          -> Stopping(target)
              -> stop RPC failure -> Failed without refresh
              -> stop RPC success -> RefreshingAfterStop(target)
                  -> both reads succeed -> Succeeded or StillPresent
                  -> either read fails -> UnverifiedSuccess
```

### Request

- Ignore a new stop request when operations are loading, a stop confirmation exists, a stop is running, or maintenance is loading.
- Resolve the requested ID against the current list.
- If absent, do not open a dialog; set a safe error telling the user to refresh.
- Opening a dialog performs zero RPC calls.
- A new request clears the previous stop message and stop error.

### Cancel

- Cancel closes the dialog and performs zero RPC calls.
- Dialog outside-click and system back use the same cancel callback.

### Confirm

Before any stop RPC, re-check all conditions:

1. A confirmation target exists.
2. No operations load, stop operation, or maintenance operation is active.
3. Installation stage is exactly `READY`.
4. The exact Job or Session still exists in the current list.

If any condition fails, clear the confirmation, return to idle, expose a fixed safe error, and perform zero stop RPC calls.

After the checks, clear the confirmation, set `stoppingTarget`, and call exactly one repository stop method with `userConfirmed = true`.

### Global operation lock

While `stopConfirmation` is non-null, the confirmation dialog is the only active operation surface. Ignore manual refresh, Job detail selection, maintenance requests, maintenance confirmation, and additional stop requests until the dialog is canceled or confirmed.

While `stoppingTarget` is non-null:

- Ignore new stop requests.
- Ignore manual Jobs/Sessions refresh.
- Ignore Job detail selection.
- Ignore maintenance requests and maintenance confirmation.
- Disable all Job and Session stop buttons.
- Disable Jobs/Sessions refresh and Job detail buttons.
- Disable maintenance controls.

While maintenance is loading, stop controls are also disabled and stop requests are ignored. This prevents two impactful operations from executing concurrently.

There is no queue. Ignored actions are not replayed later.

## Atomic operations loading

Create a feature-private result type:

```kotlin
private sealed interface OperationsLoadResult {
    data class Success(
        val jobs: List<MetasploitJobSummary>,
        val sessions: List<MetasploitSessionSummary>,
    ) : OperationsLoadResult

    data class Failure(
        val userMessage: String,
    ) : OperationsLoadResult
}
```

Create one shared loader:

```kotlin
private suspend fun loadOperationsSnapshot(): OperationsLoadResult
```

The loader must:

1. Call `operationsRepository.jobs()` once.
2. Call `operationsRepository.sessions()` once even when the Jobs read fails.
3. Build `Success` only when both calls succeed.
4. Build one safe user-facing failure message when either call fails.
5. Never expose technical messages or raw responses.

The calls may be sequential; no concurrency requirement is introduced.

### Manual refresh

- Ignore refresh while already loading, stopping, confirming a stop, or running maintenance.
- Starting an accepted manual refresh clears `stopMessage` and `stopError`.
- Set `loading=true` while preserving the previous complete lists.
- On complete success, replace Jobs and Sessions together and clear `selectedJob`, matching existing manual-refresh behavior.
- On failure, preserve the previous complete lists and selected Job, clear loading, and display the safe error.
- An initial failure leaves the initial empty snapshot intact.

This removes the current partial-update behavior where one failed source can be replaced with an empty list while the other source succeeds.

### Refresh after successful stop

After one successful stop RPC, invoke the shared loader exactly once while retaining `stoppingTarget` until the refresh result is applied.

- Both reads succeed: atomically replace both lists.
- Either read fails: preserve the complete pre-stop lists and selected Job.
- Never retry the stop or refresh automatically.
- Every terminal branch clears `stoppingTarget`.

Selected Job handling after a successful post-stop refresh:

- If the stopped target is the selected Job, clear the detail.
- If the selected Job ID no longer exists in the refreshed Jobs list, clear the detail.
- Otherwise retain the unrelated selected Job detail.
- Stopping a Session does not clear an unrelated selected Job that remains in the refreshed list.

## Completion messages

After stop RPC success and complete refresh:

- Target absent: `Job #<id> 已停止` or `Session #<id> 已停止`.
- Target still present: `停止要求已成功送出，但該項目仍出現在最新列表中。`

After stop RPC success but refresh failure:

- `停止要求已成功送出，但無法確認最新狀態。請手動重新整理。`

These messages make no stronger claim than the available evidence.

A stop message remains until the next accepted stop request, accepted manual refresh, or ViewModel destruction.

A stop RPC failure clears `stoppingTarget` and sets:

- title: `無法停止 Job #<id>` or `無法停止 Session #<id>`
- optional user message: the repository failure's safe `userMessage`

## UI design

Retain the current Dashboard and its single vertical scrolling surface.

### Job cards

Each Job card keeps:

- Job ID
- Job name
- `查看詳情`

Add a text-labeled destructive action `停止 Job`. It may use the Material error color, but the text label remains the primary risk signal. At 160% and 200% font scale, the detail and stop controls may stack vertically; no fixed horizontal width is required.

### Session cards

Each Session card keeps its current read-only details and adds the text-labeled destructive action `停止 Session` under the same sizing and accessibility rules.

No input field, command button, read action, terminal view, or batch control is added.

### Confirmation dialog

Job dialog:

```text
確認停止 Job #12？

名稱：exploit/multi/handler

停止後無法由 MAGO 復原。操作只會送出一次，不會自動重試。
```

Session dialog:

```text
確認停止 Session #3？

來源模組：<module or 尚未取得>
描述：<description or 尚未取得>

停止後此 Session 可能無法再次連線。操作只會送出一次，不會自動重試。
```

Buttons are `取消` and `確認停止`. The title always includes the type and ID. Risk is described in text and not conveyed by color alone. Dismissing the dialog returns focus to the originating card action through normal dialog focus restoration; no custom focus trap is added.

### Running state

- Close the dialog after confirmation.
- Show `正在停止 Job #<id>` or `正在停止 Session #<id>` in the Jobs/Sessions section.
- When reduced motion is disabled, the existing linear progress treatment may be shown.
- When reduced motion is enabled, show static text only.
- Do not use Toast-only feedback.

### Failure display

The UI may show `OperationStopError.title` and its optional safe `userMessage`. It must not display or retain:

- `technicalMessage`
- `diagnosticData`
- stack traces
- RPC request or response bodies
- token or endpoint
- arbitrary exception text

Fixed pre-RPC messages:

- Missing target: `此 Job 已不在目前列表中，請重新整理。` or the Session equivalent.
- Not READY: `RPC 環境尚未就緒，未送出停止要求。`

## Security and safety properties

- Localhost RPC policy remains unchanged.
- OkHttp automatic connection retry remains disabled.
- A confirmation dialog alone does not authorize a stop; the service also requires the explicit confirmation value.
- Invalid IDs and missing tokens fail before transport.
- Every confirmed action produces at most one stop RPC.
- No automatic stop retry, refresh retry, polling, queue, or background execution.
- No new persistence, permissions, analytics, logs, reports, or diagnostic fields.
- No Session input/output enters memory because Session interaction is not implemented.

## Tests

### Core RPC service

1. Unconfirmed Job stop performs zero transport calls.
2. Unconfirmed Session stop performs zero transport calls.
3. Non-numeric, negative, and overflowing Job IDs perform zero transport calls.
4. Negative Session ID performs zero transport calls.
5. Job stop sends `job.stop` once with one integer argument.
6. Session stop sends `session.stop` once with one integer argument.
7. Case-insensitive `result=success` succeeds.
8. Non-map, missing result, non-string result, and other string results fail with the specified codes.
9. Transport failure is propagated and not retried.

### Repository

1. Missing token performs zero stop-service calls.
2. Authenticated Job stop forwards the ID and confirmation once.
3. Authenticated Session stop forwards the ID and confirmation once.
4. Repository code does not log or persist token, ID, response, or error detail.

### Dashboard ViewModel

1. Request opens confirmation and performs zero RPC calls.
2. Cancel performs zero RPC calls.
3. Confirmation while not READY performs zero stop RPC calls.
4. Confirmation after the target disappears performs zero stop RPC calls.
5. A confirmation dialog blocks refresh, Job detail, maintenance, and a second stop request.
6. A global stop blocks a second stop, refresh, Job detail load, and maintenance action.
7. Maintenance loading blocks stop requests.
8. Confirmation invokes only the matching stop method once.
9. Stop failure performs zero post-stop reads.
10. Stop success calls Jobs once and Sessions once.
11. Complete post-stop refresh atomically replaces both lists.
12. Either post-stop read failure preserves both old lists and selected Job.
13. Target absent after refresh shows the stopped message.
14. Target present after refresh shows the still-present message.
15. Stopping the selected Job clears its detail.
16. Stopping another Job retains selected Job only when it still exists.
17. Stopping a Session retains an unrelated selected Job when it still exists.
18. Manual refresh failure preserves the previous complete snapshot.
19. Manual refresh success replaces both lists and clears selected Job.
20. Accepted manual refresh clears old stop feedback.
21. Every success or failure terminal branch clears the global stop lock.

Do not add broad screenshot tests. Compose UI tests are optional only if the existing test infrastructure can verify the dialog without introducing a new instrumentation stack. Pure service and ViewModel tests are mandatory.

## CI and acceptance

The existing Android workflow must continue to verify the Bridge contract and embedded bundle reproducibility, then run at least:

```text
:app:assembleDebug
:app:lintDebug
:core:rpc:testDebugUnitTest
:feature:dashboard:testDebugUnitTest
```

All existing risk-directed tests remain in the gate.

Real-device smoke checks:

- Canceling the dialog does not stop the item.
- A Job stop updates the list after one automatic verification refresh.
- A Session stop updates the list after one automatic verification refresh.
- Rapid repeated taps produce only one stop RPC.
- Other stop, refresh, detail, and maintenance controls are disabled while stopping.
- Offline RPC never produces a misleading success message.
- Dialog and buttons remain usable at 160% and 200% font scale.
- Reduced-motion mode uses static progress text.

## Explicit exclusions

Phase 8A does not include:

- Session interactive read or write
- Meterpreter APIs
- console commands
- arbitrary RPC methods
- batch stop or stop-all
- automated post-exploitation
- credential collection
- automatic polling or retry
- background services or notifications
- persistent operation history
- a new navigation destination
- a new module or database schema
- new Android permissions
