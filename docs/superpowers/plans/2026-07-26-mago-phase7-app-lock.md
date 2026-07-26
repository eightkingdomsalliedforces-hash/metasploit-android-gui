# App Lock and Sensitive Screen Protection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an optional fail-closed App lock using Android system authentication and prevent sensitive screen capture.

**Architecture:** A pure reducer in `core:security` owns lock transitions. `core:datastore` persists only whether App lock is enabled. `MainActivity` applies `FLAG_SECURE`, invokes Jetpack `BiometricPrompt`, and does not compose MAGO feature screens until settings are loaded and the lock is open.

**Tech Stack:** Kotlin 2.4, AndroidX Biometric 1.1.0, Preferences DataStore 1.2.1, StateFlow, Compose Material 3.

## Global Constraints

- App lock is disabled by default and never enabled silently.
- Enabling and disabling require fresh system authentication.
- Allowed authenticators are `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
- No biometric templates, PINs, passwords, or authentication results are persisted.
- Startup is fail-closed while security settings load.
- Enabled App lock locks immediately when the Activity leaves the foreground, except configuration changes.
- Locked state does not compose feature screens or start their ViewModel collection.
- `FLAG_SECURE` is always active.
- Tests cover only reducer transitions and fail-closed behavior; no broad Compose suite.

---

### Task 1: Add pure lock state machine

**Files:**
- Create: `core/security/src/main/kotlin/dev/mago/android/security/AppLockStateMachine.kt`
- Test: `core/security/src/test/kotlin/dev/mago/android/security/AppLockStateMachineTest.kt`

- [ ] Verify enabled settings load into locked state.
- [ ] Verify disabled settings load into unlocked state.
- [ ] Verify background locks only when enabled.
- [ ] Verify successful authentication unlocks.
- [ ] Verify authentication failure remains locked.

### Task 2: Persist the enable flag

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/datastore/build.gradle.kts`
- Create: `core/datastore/src/main/kotlin/dev/mago/android/datastore/AppLockSettingsStore.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/AppContainer.kt`

- [ ] Add `androidx.datastore:datastore-preferences`.
- [ ] Store only `app_lock_enabled`, default false.
- [ ] Create one DataStore instance through AppContainer.

### Task 3: Integrate system authentication and secure window

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/dev/mago/android/AppLockViewModel.kt`
- Create: `app/src/main/kotlin/dev/mago/android/AppLockScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

- [ ] Add stable AndroidX Biometric 1.1.0 and `USE_BIOMETRIC`.
- [ ] Convert MainActivity to FragmentActivity and configure BiometricPrompt.
- [ ] Apply `FLAG_SECURE` before composing content.
- [ ] Lock on `onStop` unless changing configurations.
- [ ] Compose only loading/lock UI until unlocked.
- [ ] Authenticate before enable, disable, or unlock.

### Task 4: Expose explicit security setting

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

- [ ] Add a security card showing enabled/disabled state.
- [ ] Provide one explicit enable/disable button.
- [ ] Explain that system biometric or device credential is used and no biometric data is stored.

### Task 5: Verify and integrate

- [ ] Run GitHub Android Build, Lint, Bridge validation, and core security tests.
- [ ] Verify debug APK Artifact.
- [ ] Run unsigned Release workflow because dependencies and manifest changed.
- [ ] Merge only after both workflows pass.
