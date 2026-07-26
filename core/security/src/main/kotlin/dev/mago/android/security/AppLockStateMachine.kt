package dev.mago.android.security

data class AppLockState(
    val initialized: Boolean = false,
    val enabled: Boolean = false,
    val locked: Boolean = true,
)

sealed interface AppLockEvent {
    data class SettingsLoaded(val enabled: Boolean) : AppLockEvent
    data object AppBackgrounded : AppLockEvent
    data object AuthenticationSucceeded : AppLockEvent
    data object AuthenticationFailed : AppLockEvent
}

class AppLockStateMachine {
    fun reduce(state: AppLockState, event: AppLockEvent): AppLockState = when (event) {
        is AppLockEvent.SettingsLoaded -> {
            if (!state.initialized) {
                AppLockState(
                    initialized = true,
                    enabled = event.enabled,
                    locked = event.enabled,
                )
            } else {
                state.copy(
                    enabled = event.enabled,
                    locked = if (event.enabled) state.locked else false,
                )
            }
        }

        AppLockEvent.AppBackgrounded -> {
            if (state.initialized && state.enabled) state.copy(locked = true) else state
        }

        AppLockEvent.AuthenticationSucceeded -> {
            if (state.initialized) state.copy(locked = false) else state
        }

        AppLockEvent.AuthenticationFailed -> state
    }
}
