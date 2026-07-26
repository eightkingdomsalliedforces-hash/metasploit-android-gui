package dev.mago.android

internal fun tryWriteDiagnosticsClipboard(write: () -> Unit): Boolean =
    try {
        write()
        true
    } catch (_: Exception) {
        false
    }
