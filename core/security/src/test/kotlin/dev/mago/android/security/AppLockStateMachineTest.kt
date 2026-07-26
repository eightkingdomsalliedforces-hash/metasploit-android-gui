package dev.mago.android.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLockStateMachineTest {
    private val machine = AppLockStateMachine()

    @Test
    fun `enabled setting loads fail closed`() {
        val state = machine.reduce(AppLockState(), AppLockEvent.SettingsLoaded(enabled = true))

        assertThat(state.initialized).isTrue()
        assertThat(state.enabled).isTrue()
        assertThat(state.locked).isTrue()
    }

    @Test
    fun `disabled setting loads unlocked`() {
        val state = machine.reduce(AppLockState(), AppLockEvent.SettingsLoaded(enabled = false))

        assertThat(state.initialized).isTrue()
        assertThat(state.enabled).isFalse()
        assertThat(state.locked).isFalse()
    }

    @Test
    fun `background locks only when enabled`() {
        val enabled = AppLockState(initialized = true, enabled = true, locked = false)
        val disabled = AppLockState(initialized = true, enabled = false, locked = false)

        assertThat(machine.reduce(enabled, AppLockEvent.AppBackgrounded).locked).isTrue()
        assertThat(machine.reduce(disabled, AppLockEvent.AppBackgrounded).locked).isFalse()
    }

    @Test
    fun `successful authentication unlocks initialized state`() {
        val locked = AppLockState(initialized = true, enabled = true, locked = true)

        assertThat(machine.reduce(locked, AppLockEvent.AuthenticationSucceeded).locked).isFalse()
    }

    @Test
    fun `failed authentication preserves locked state`() {
        val locked = AppLockState(initialized = true, enabled = true, locked = true)

        assertThat(machine.reduce(locked, AppLockEvent.AuthenticationFailed)).isEqualTo(locked)
    }

    @Test
    fun `disabling an initialized lock always reveals the app`() {
        val locked = AppLockState(initialized = true, enabled = true, locked = true)

        val disabled = machine.reduce(locked, AppLockEvent.SettingsLoaded(enabled = false))

        assertThat(disabled.enabled).isFalse()
        assertThat(disabled.locked).isFalse()
    }
}
