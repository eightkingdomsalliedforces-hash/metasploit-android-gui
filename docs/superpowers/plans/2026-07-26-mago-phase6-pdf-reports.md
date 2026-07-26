# MAGO Phase 6 PDF Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standalone A4 PDF export generated on-device from the same bounded, redacted report snapshot used by JSON, CSV, HTML, and ZIP.

**Architecture:** A pure Kotlin `PdfReportContentBuilder` projects `ReportSnapshot` into ordered safe text blocks and is unit-tested for secret exclusion. `PdfReportDocumentBuilder` is the only Android renderer; it consumes those blocks, wraps text onto A4 pages with `PdfDocument`/Canvas, and returns bytes through the existing `ReportDocument` contract. A composite builder routes PDF to the Android renderer and all other formats to the existing deterministic builder.

**Tech Stack:** Kotlin, Android `PdfDocument`, Canvas/Paint, SAF, JUnit4/Truth, GitHub Actions.

## Global Constraints

- No new RPC method, data source, permission, background task, or automatic retry.
- PDF uses the existing active-Workspace snapshot limits: 100 Hosts, Services, Vulnerabilities, and executions.
- PDF content excludes RPC passwords, Tokens, Credentials, Keystore, Console, asset free text, `extraFields`, module results, and module errors.
- Sensitive option values are masked before any text reaches Android Canvas.
- No custom font file is bundled or shared; rendering uses Android system typefaces.
- ZIP continues to contain only JSON, CSV, and HTML.

---

### Task 1: Build Safe PDF Content Model

- [ ] Add `ReportFormat.PDF` with MIME `application/pdf`.
- [ ] Create typed PDF blocks for title, heading, paragraph, and table-style rows.
- [ ] Build blocks only from the same whitelisted asset fields and re-masked option map.
- [ ] Test that injected secrets from every excluded source never occur in block text.
- [ ] Test deterministic ordering and bounded summary labels.

### Task 2: Render Multi-page A4 PDF

- [ ] Render A4 pages at 595×842 points with fixed margins.
- [ ] Wrap text using `Paint.breakText` and create a new page before the bottom margin.
- [ ] Use system sans-serif typefaces and no external resources.
- [ ] Close every page and the `PdfDocument` in `try/finally`.
- [ ] Return a `ReportDocument` using the existing filename/id convention.

### Task 3: Route Builder and SAF Picker

- [ ] Add a `CompositeReportDocumentBuilder` that delegates PDF only to the Android renderer.
- [ ] Keep the existing builder responsible for JSON, CSV, HTML, and ZIP.
- [ ] Configure AppContainer with the composite builder.
- [ ] Add one `CreateDocument("application/pdf")` launcher and exhaustive format routing.

### Task 4: CI and Verification

- [ ] Run pure PDF content tests, existing report security tests, Android Build, and Lint.
- [ ] Fix only evidence-backed failures.
- [ ] Verify Artifact digest, ZIP integrity, APK size, and APK SHA-256 before merge.
