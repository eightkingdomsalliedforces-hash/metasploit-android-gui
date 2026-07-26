package dev.mago.android.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

enum class FontScale(val percent: Int, val multiplier: Float) {
    NORMAL(100, 1.0f),
    LARGE(130, 1.3f),
    EXTRA_LARGE(160, 1.6f),
    MAXIMUM(200, 2.0f),
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.NORMAL,
    val reducedMotion: Boolean = false,
)

class UserPreferencesRepository private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            UserPreferences(
                themeMode = ThemeMode.entries.getOrElse(values[THEME_MODE] ?: ThemeMode.SYSTEM.ordinal) {
                    ThemeMode.SYSTEM
                },
                fontScale = FontScale.entries.getOrElse(values[FONT_SCALE] ?: FontScale.NORMAL.ordinal) {
                    FontScale.NORMAL
                },
                reducedMotion = values[REDUCED_MOTION] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { values -> values[THEME_MODE] = mode.ordinal }
    }

    suspend fun setFontScale(scale: FontScale) {
        dataStore.edit { values -> values[FONT_SCALE] = scale.ordinal }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        dataStore.edit { values -> values[REDUCED_MOTION] = enabled }
    }

    companion object {
        private const val FILE_NAME = "user_preferences.preferences_pb"
        private val THEME_MODE = intPreferencesKey("theme_mode")
        private val FONT_SCALE = intPreferencesKey("font_scale")
        private val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")

        fun create(context: Context): UserPreferencesRepository {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return UserPreferencesRepository(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
                ),
            )
        }
    }
}
