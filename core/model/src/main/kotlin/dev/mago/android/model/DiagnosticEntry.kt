package dev.mago.android.model

data class DiagnosticEntry(
    val key: String,
    val label: String,
    val value: String,
    val sensitive: Boolean = false,
)
