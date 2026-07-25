package dev.mago.android.model.bridge

import kotlinx.serialization.Serializable

@Serializable
enum class BridgeAction {
    INSTALL_METASPLOIT,
    REPAIR_METASPLOIT,
    INITIALIZE_DATABASE,
    START_SERVICES,
    STOP_SERVICES,
    START_RPC,
    STOP_RPC,
    UPDATE_METASPLOIT,
    BACKUP_ENVIRONMENT,
    RESTORE_ENVIRONMENT,
    HEALTH_CHECK,
    CLEAN_CACHE,
}
