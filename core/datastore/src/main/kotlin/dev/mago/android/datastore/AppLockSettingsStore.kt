package dev.mago.android.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface AppLockSettingsStore {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}

class PreferencesAppLockSettingsStore(
    context: Context,
) : AppLockSettingsStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
    )

    override val enabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[APP_LOCK_ENABLED] ?: false }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[APP_LOCK_ENABLED] = enabled
        }
    }

    private companion object {
        const val FILE_NAME = "mago_security.preferences_pb"
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }
}
