package com.selfguard.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BlockedSiteEntity::class, BlockedKeywordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SelfGuardDatabase : RoomDatabase() {
    abstract fun siteDao(): BlockedSiteDao
    abstract fun keywordDao(): BlockedKeywordDao
}
