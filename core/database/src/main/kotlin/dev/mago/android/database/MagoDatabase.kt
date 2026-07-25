package dev.mago.android.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.mago.android.database.dao.InstallationStateDao
import dev.mago.android.database.entity.InstallationStateEntity

@Database(
    entities = [InstallationStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MagoDatabase : RoomDatabase() {
    abstract fun installationStateDao(): InstallationStateDao

    companion object {
        fun create(context: Context): MagoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MagoDatabase::class.java,
                "mago.db",
            ).build()
    }
}
