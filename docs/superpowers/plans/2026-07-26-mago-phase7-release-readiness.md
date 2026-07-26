# Release Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Produce versioned unsigned Release artifacts with checksums and improve service-status screen-reader semantics without claiming store-ready signing.

**Architecture:** The Android application convention plugin reads version properties with safe defaults. A separate manual GitHub Actions workflow validates requested version input, builds Release APK/AAB, copies them to fixed artifact names, and emits SHA-256 checksums. `ServiceStatusCard` exposes its visible status through Compose semantics while keeping the icon decorative.

**Tech Stack:** AGP 9.3, Gradle 9.5, Kotlin 2.4, GitHub Actions, Compose semantics.

## Global Constraints

- Release artifacts remain explicitly unsigned; no signing keys or secrets are added to the repository.
- The workflow is manual only and does not publish a GitHub Release or Play Store upload.
- Version name accepts digits separated by dots with an optional conservative prerelease suffix.
- Version code must be a positive integer.
- Existing PR CI remains unchanged.
- No broad UI test suite; accessibility is verified through compilation/Lint and a focused semantics implementation.

---

### Task 1: Parameterize Android version metadata

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/MagoAndroidApplicationPlugin.kt`
- Modify: `gradle.properties`

- [ ] Read `mago.versionName` and `mago.versionCode` from Gradle properties.
- [ ] Fail configuration on invalid or non-positive version code.
- [ ] Use `0.7.0` and `7` as repository defaults.

### Task 2: Add manual unsigned Release workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] Require `versionName` and `versionCode` workflow inputs.
- [ ] Validate inputs before Gradle execution.
- [ ] Run Bridge validation, `bundleRelease`, `assembleRelease`, and `lintRelease`.
- [ ] Copy outputs to fixed names containing the requested version.
- [ ] Generate `SHA256SUMS.txt` and upload a single Artifact.
- [ ] Never read or request signing secrets.

### Task 3: Improve status accessibility

**Files:**
- Modify: `core/ui/src/main/kotlin/dev/mago/android/ui/components/ServiceStatusCard.kt`

- [ ] Add merged semantics with `stateDescription` equal to the visible localized status.
- [ ] Keep the status icon decorative to avoid duplicate speech.
- [ ] Run existing Android Build and Lint.

### Task 4: Verify and integrate

- [ ] Open a PR and run normal Android CI.
- [ ] Manually dispatch the Release workflow with `0.7.0 / 7`.
- [ ] Verify APK/AAB names and SHA-256 manifest.
- [ ] Merge only after both workflows pass.
