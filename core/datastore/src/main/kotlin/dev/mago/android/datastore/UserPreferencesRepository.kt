package dev.mago.android.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dev.mago.android.datastore.proto.UserPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class UserPreferencesRepository(private val dataStore: DataStore<UserPreferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(UserPreferences.getDefaultInstance()) else throw error
    }

    suspend fun setThemeMode(mode: UserPreferences.ThemeMode) {
        dataStore.updateData { current -> current.toBuilder().setThemeMode(mode).build() }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        dataStore.updateData { current -> current.toBuilder().setReducedMotion(enabled).build() }
    }

    companion object {
        fun create(context: Context): UserPreferencesRepository = UserPreferencesRepository(
            DataStoreFactory.create(
                serializer = UserPreferencesSerializer,
                produceFile = { context.applicationContext.dataStoreFile("user_preferences.pb") },
            ),
        )
    }
}
