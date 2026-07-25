package dev.mago.android.model

data class MetasploitConsoleSnapshot(
    val id: String,
    val prompt: String,
    val busy: Boolean,
    val output: String,
)
