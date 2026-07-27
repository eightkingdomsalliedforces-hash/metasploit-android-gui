# Phase 8A Mandatory State-Machine Test Companion

This file is a mandatory companion to `2026-07-26-phase8a-single-operation-stop.md`. Add all four tests to `feature/dashboard/src/test/kotlin/dev/mago/android/dashboard/DashboardViewModelTest.kt` during Task 4, before the Task 4 GREEN run and commit. They close four approved state-machine requirements that must not be omitted.

The companion assumes the programmable `FakeOperationsRepository`, `FakeCoordinator`, `FakeTermuxGateway`, `dispatcher`, and imports defined by the main implementation plan.

## 1. Target disappears after confirmation opens

```kotlin
@Test
fun `target removed after confirmation performs zero stop calls`() = runTest {
    val mutableJobs = mutableListOf(MetasploitJobSummary("2", "Example Job"))
    val repository = FakeOperationsRepository().apply {
        jobsResult = AppResult.Success(mutableJobs)
    }
    val viewModel = DashboardViewModel(
        FakeCoordinator(InstallationStage.READY),
        repository,
        FakeTermuxGateway(),
    )
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopJob("2")
    assertThat(viewModel.uiState.value.stopConfirmation)
        .isEqualTo(OperationStopTarget.Job("2", "Example Job"))

    mutableJobs.clear()
    viewModel.confirmStop()
    advanceUntilIdle()

    assertThat(repository.stopJobCalls).isEmpty()
    assertThat(viewModel.uiState.value.stopConfirmation).isNull()
    assertThat(viewModel.uiState.value.stoppingTarget).isNull()
    assertThat(viewModel.uiState.value.stopError?.title)
        .isEqualTo("此 Job 已不在目前列表中，請重新整理。")
    collection.cancel()
}
```

## 2. Stop confirmation blocks every other operation surface

```kotlin
@Test
fun `stop confirmation blocks refresh detail maintenance and second stop`() = runTest {
    val repository = FakeOperationsRepository()
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
    viewModel.requestStopSession(7)
    viewModel.refreshOperations()
    viewModel.selectJob("2")
    viewModel.requestMaintenance(MaintenanceAction.CLEAN_CACHE)
    viewModel.confirmMaintenance()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.stopConfirmation)
        .isEqualTo(OperationStopTarget.Job("2", "Example Job"))
    assertThat(repository.stopJobCalls).isEmpty()
    assertThat(repository.stopSessionCalls).isEmpty()
    assertThat(repository.jobsCalls).isEqualTo(jobsCalls)
    assertThat(repository.sessionsCalls).isEqualTo(sessionsCalls)
    assertThat(repository.jobInfoCalls).isEmpty()
    assertThat(viewModel.uiState.value.maintenanceConfirmation).isNull()
    assertThat(gateway.actions).isEmpty()
    collection.cancel()
}
```

## 3. Accepted manual refresh clears an old stop success message

```kotlin
@Test
fun `accepted manual refresh clears old stop message`() = runTest {
    val repository = FakeOperationsRepository()
    val viewModel = DashboardViewModel(
        FakeCoordinator(InstallationStage.READY),
        repository,
        FakeTermuxGateway(),
    )
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopSession(7)
    viewModel.confirmStop()
    advanceUntilIdle()
    assertThat(viewModel.uiState.value.stopMessage)
        .isEqualTo("停止要求已成功送出，但該項目仍出現在最新列表中。")

    viewModel.refreshOperations()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.stopMessage).isNull()
    assertThat(viewModel.uiState.value.stopError).isNull()
    collection.cancel()
}
```

## 4. Accepted manual refresh clears an old stop error

```kotlin
@Test
fun `accepted manual refresh clears old stop error`() = runTest {
    val repository = FakeOperationsRepository().apply {
        stopSessionResult = AppResult.Failure(
            AppError(errorCode = "STOP_FAILED", userMessage = "stop failed"),
        )
    }
    val viewModel = DashboardViewModel(
        FakeCoordinator(InstallationStage.READY),
        repository,
        FakeTermuxGateway(),
    )
    val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.requestStopSession(7)
    viewModel.confirmStop()
    advanceUntilIdle()
    assertThat(viewModel.uiState.value.stopError?.title)
        .isEqualTo("無法停止 Session #7")

    viewModel.refreshOperations()
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.stopMessage).isNull()
    assertThat(viewModel.uiState.value.stopError).isNull()
    collection.cancel()
}
```

## Required verification

Run after adding the tests and before the Task 4 commit:

```bash
gradle --no-daemon --stacktrace :feature:dashboard:testDebugUnitTest \
  --tests 'dev.mago.android.dashboard.DashboardViewModelTest'
```

Expected: all existing Task 4 tests and these four companion tests pass. Do not proceed to Task 5 while any test fails.
