package com.selfguard.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BlockedSiteEntity::class, BlockedKeywordEntity::class, BlockedAppEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SelfGuardDatabase : RoomDatabase() {
    abstract fun siteDao(): BlockedSiteDao
    abstract fun keywordDao(): BlockedKeywordDao
    abstract fun appDao(): BlockedAppDao

    companion object {
        /** Adds blocked_apps; existing site and keyword rows are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_apps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
