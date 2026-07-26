package dev.mago.android.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.mago.android.database.dao.InstallationStateDao
import dev.mago.android.database.dao.ModuleCatalogDao
import dev.mago.android.database.dao.ModuleHistoryDao
import dev.mago.android.database.entity.AuditEventEntity
import dev.mago.android.database.entity.InstallationStateEntity
import dev.mago.android.database.entity.ModuleCatalogEntity
import dev.mago.android.database.entity.ModuleExecutionEntity
import dev.mago.android.database.entity.ModuleFavoriteEntity
import dev.mago.android.database.entity.ModuleRecentEntity

@Database(
    entities = [
        InstallationStateEntity::class,
        ModuleCatalogEntity::class,
        ModuleFavoriteEntity::class,
        ModuleRecentEntity::class,
        ModuleExecutionEntity::class,
        AuditEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MagoDatabase : RoomDatabase() {
    abstract fun installationStateDao(): InstallationStateDao
    abstract fun moduleCatalogDao(): ModuleCatalogDao
    abstract fun moduleHistoryDao(): ModuleHistoryDao

    companion object {
        fun create(context: Context): MagoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MagoDatabase::class.java,
                "mago.db",
            )
                .addMigrations(MagoDatabaseMigrations.MIGRATION_1_2)
                .build()
    }
}
