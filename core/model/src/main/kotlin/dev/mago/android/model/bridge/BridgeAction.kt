package dev.mago.android.model.bridge

import kotlinx.serialization.Serializable

@Serializable
enum class BridgeAction {
    UPDATE_PACKAGES,
    INSTALL_DEPENDENCIES,
    INSTALL_METASPLOIT,
    REPAIR_METASPLOIT,
    INITIALIZE_DATABASE,
    CONFIGURE_RPC,
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
