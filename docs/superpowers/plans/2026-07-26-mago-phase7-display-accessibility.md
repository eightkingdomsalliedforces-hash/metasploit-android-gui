# Display and Accessibility Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Persist and apply theme, reduced-motion, and 100–200% font-scale preferences across the lock screen and all MAGO feature screens.

**Architecture:** The existing Preferences DataStore repository owns display preferences. A small App-level ViewModel observes and updates them. `MagoTheme` applies color mode, font scale through `LocalDensity`, and a `LocalReducedMotion` composition local. Dashboard exposes explicit controls without adding another crowded navigation destination.

**Tech Stack:** Kotlin 2.4, Preferences DataStore 1.2.1, Compose Material 3, StateFlow.

## Global Constraints

- Supported themes: system, light, dark, AMOLED.
- Supported font scales: 100%, 130%, 160%, 200% only.
- Invalid persisted ordinals fall back to system theme and 100% font.
- Reduced motion is off by default.
- Preferences apply to the App lock screen and unlocked App.
- At 160% and 200%, phone bottom navigation does not render six enlarged labels simultaneously.
- Do not add screenshot tests or broad UI suites.

---

### Task 1: Extend Preferences DataStore

**Files:**
- Modify: `core/datastore/src/main/kotlin/dev/mago/android/datastore/UserPreferencesRepository.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/AppContainer.kt`

- [ ] Add `FontScale` values 100/130/160/200.
- [ ] Persist font-scale ordinal with safe fallback.
- [ ] Expose one App-scoped UserPreferencesRepository.

### Task 2: Apply preferences globally

**Files:**
- Modify: `core/ui/src/main/kotlin/dev/mago/android/ui/theme/MagoTheme.kt`
- Create: `core/ui/src/main/kotlin/dev/mago/android/ui/accessibility/AccessibilityLocals.kt`
- Create: `app/src/main/kotlin/dev/mago/android/UserPreferencesViewModel.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/AppLockScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

- [ ] Apply theme mode and font scale before rendering lock or feature screens.
- [ ] Provide reduced-motion state through a composition local.
- [ ] Keep DataStore errors visible without blocking App lock security.

### Task 3: Add explicit dashboard controls

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/dev/mago/android/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/MainActivity.kt`

- [ ] Add theme chips.
- [ ] Add 100/130/160/200 font chips.
- [ ] Add reduced-motion toggle.
- [ ] Show save failures.

### Task 4: Make navigation tolerate 200% font

**Files:**
- Modify: `app/src/main/kotlin/dev/mago/android/MagoApp.kt`

- [ ] Pass theme/font/reduced-motion into MagoTheme.
- [ ] Hide simultaneous phone navigation labels at 160% or above while retaining icon content descriptions.
- [ ] Keep tablet rail labels.

### Task 5: Verify and integrate

- [ ] Run Android Build, Lint, Bridge validation, and existing risk-based tests.
- [ ] Run unsigned Release APK/AAB verification.
- [ ] Verify artifacts and merge only when both workflows pass.
