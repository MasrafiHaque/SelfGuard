package com.selfguard.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocked_sites")
data class BlockedSiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val label: String = "",
    val includeSubdomains: Boolean = true,
    val isEnabled: Boolean = true,
    val source: String = "default",
    val addedAt: Long = 0L,
)

@Entity(tableName = "blocked_keywords")
data class BlockedKeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val label: String = "",
    val matchInPageText: Boolean = true,
    val isEnabled: Boolean = true,
    val source: String = "default",
    val addedAt: Long = 0L,
)

@Dao
interface BlockedSiteDao {
    @Query("SELECT * FROM blocked_sites ORDER BY domain ASC")
    fun observeAll(): Flow<List<BlockedSiteEntity>>

    @Query("SELECT * FROM blocked_sites")
    suspend fun getAll(): List<BlockedSiteEntity>

    @Query("SELECT COUNT(*) FROM blocked_sites")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BlockedSiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlockedSiteEntity): Long

    @Query("UPDATE blocked_sites SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM blocked_sites WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM blocked_sites")
    suspend fun deleteAll()
}

@Dao
interface BlockedKeywordDao {
    @Query("SELECT * FROM blocked_keywords ORDER BY keyword ASC")
    fun observeAll(): Flow<List<BlockedKeywordEntity>>

    @Query("SELECT * FROM blocked_keywords")
    suspend fun getAll(): List<BlockedKeywordEntity>

    @Query("SELECT COUNT(*) FROM blocked_keywords")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BlockedKeywordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlockedKeywordEntity): Long

    @Query("UPDATE blocked_keywords SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM blocked_keywords WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM blocked_keywords")
    suspend fun deleteAll()
}
