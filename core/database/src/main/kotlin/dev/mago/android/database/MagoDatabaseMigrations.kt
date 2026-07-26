package dev.mago.android.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MagoDatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `module_catalog` (
                    `type` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `rank` TEXT,
                    `refreshedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`type`, `name`)
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_module_catalog_type` ON `module_catalog` (`type`)")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `module_favorite` (
                    `type` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`type`, `name`)
                )""".trimIndent(),
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `module_recent` (
                    `type` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `lastOpenedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`type`, `name`)
                )""".trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_module_recent_lastOpenedAtEpochMillis` " +
                    "ON `module_recent` (`lastOpenedAtEpochMillis`)",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `module_execution` (
                    `correlationId` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `jobId` INTEGER,
                    `uuid` TEXT,
                    `redactedOptions` TEXT NOT NULL,
                    `resultSummary` TEXT,
                    `error` TEXT,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`correlationId`)
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_module_execution_uuid` ON `module_execution` (`uuid`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_module_execution_updatedAtEpochMillis` " +
                    "ON `module_execution` (`updatedAtEpochMillis`)",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `audit_event` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `correlationId` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `moduleName` TEXT NOT NULL,
                    `result` TEXT NOT NULL,
                    `redactedOptions` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_event_correlationId` ON `audit_event` (`correlationId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_audit_event_createdAtEpochMillis` " +
                    "ON `audit_event` (`createdAtEpochMillis`)",
            )
        }
    }
}
