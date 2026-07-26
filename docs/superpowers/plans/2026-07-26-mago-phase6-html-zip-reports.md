# MAGO Phase 6 HTML and ZIP Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing redacted reporting pipeline with standalone HTML and a ZIP bundle containing JSON, CSV, and HTML representations of the same safe snapshot.

**Architecture:** Reuse `ReportSnapshot` and the existing whitelist serializers. HTML receives its own strict escaping helper and contains no scripts or remote resources. ZIP is produced in memory with fixed entry names and timestamps, and contains only serializer outputs generated from the same snapshot.

**Tech Stack:** Kotlin/JVM, `ZipOutputStream`, Android SAF, JUnit4/Truth, GitHub Actions.

## Global Constraints

- No new data source, RPC method, permission, background work, or retry.
- HTML contains no JavaScript, external URL, remote stylesheet, or embedded attachment.
- ZIP contains exactly `report.json`, `report.csv`, and `report.html`.
- All formats use the same whitelist and export-time sensitive-option masking.
- ZIP entry names are fixed; Workspace or user input never becomes a ZIP path.
- PDF remains a later independent slice.

---

### Task 1: Add HTML Serializer

- [ ] Add `ReportFormat.HTML` with MIME `text/html`.
- [ ] Generate semantic UTF-8 HTML tables for metadata, assets, and execution history.
- [ ] Escape `&`, `<`, `>`, `"`, and `'` in every dynamic value.
- [ ] Add tests proving markup injection and secret values cannot appear unescaped or unmasked.

### Task 2: Add Deterministic ZIP Bundle

- [ ] Add `ReportFormat.ZIP` with MIME `application/zip`.
- [ ] Package only `report.json`, `report.csv`, and `report.html` in fixed order.
- [ ] Set every ZIP entry timestamp to zero and reject duplicate entry names by construction.
- [ ] Add tests that enumerate entries and re-run secret-exclusion assertions against every extracted text file.

### Task 3: Extend SAF Picker and UI

- [ ] Let the existing format chips display HTML and ZIP automatically.
- [ ] Add dedicated `CreateDocument` launchers for `text/html` and `application/zip`.
- [ ] Keep cancellation, save status, and fail-closed write behavior unchanged.

### Task 4: CI and Verification

- [ ] Run existing reporting tests, feature tests, Android Build, and Lint.
- [ ] Fix only evidence-backed failures.
- [ ] Verify Artifact digest, ZIP integrity, APK size, and APK SHA-256 before merge.
