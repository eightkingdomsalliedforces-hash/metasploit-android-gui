# MAGO Phase 7C About and Diagnostics Design

This implementation branch follows the approved design from `design/phase7c-about-diagnostics`.

## Product decisions

- About is integrated at the top of the existing Diagnostics destination.
- The summary omits device brand and model.
- The summary includes safe installation stage, last successful stage, failure category, and safe error code.
- Device identifiers, credentials, tokens, paths, raw errors, stack traces, Bridge raw output, and `AppError.diagnosticData` are excluded.
- Diagnostics are copied manually and are never uploaded automatically.
- Entering the page triggers no RPC, Bridge action, retry, polling, background job, or network request.

## Architecture

The App module supplies primitive build/platform values and passive coordinator state to `DiagnosticsPresentationInput`. The Diagnostics feature applies an exact ordered Bridge allowlist plus defensive deny rules, creates a typed `DiagnosticsUiModel`, and produces a stable copy summary. Only the App module accesses Android ClipboardManager.

## Exact Bridge allowlist

- `bridge.frameworkRepository`
- `bridge.msfconsole`
- `bridge.databaseInitialized`
- `bridge.databaseConfig`
- `bridge.databaseReady`
- `bridge.rpcConfigured`
- `bridge.rpcProcessRunning`
- `bridge.rpcPortOpen`
- `bridge.rpcHost`
- `bridge.rpcPort`
- `bridge.metasploitVersion`

`bridge.rpcHost` is converted to localhost yes/no/unknown and is never displayed or copied verbatim. Unknown keys fail closed.