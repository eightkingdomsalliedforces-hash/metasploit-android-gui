package dev.mago.android.model

enum class ServiceStatus {
    UNKNOWN,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR,
    PERMISSION_REQUIRED,
}
