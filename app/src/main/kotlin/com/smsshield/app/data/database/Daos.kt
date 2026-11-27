package com.smsshield.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuarantineDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: QuarantineEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM quarantine_messages WHERE dedupKey = :dedupKey)")
    suspend fun existsByDedupKey(dedupKey: String): Boolean

    @Query("SELECT * FROM quarantine_messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<QuarantineEntity>>

    @Query(
        "SELECT COUNT(*) FROM quarantine_messages " +
            "WHERE verdict = 'SPAM' AND wasDismissed = 1 AND isFalsePositive = 0 AND timestamp >= :startOfDayMillis"
    )
    fun observeBlockedTodayCount(startOfDayMillis: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM quarantine_messages " +
            "WHERE verdict = 'SPAM' AND wasDismissed = 1 AND isFalsePositive = 0"
    )
    fun observeTotalBlockedCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM quarantine_messages " +
            "WHERE verdict = 'SUSPICIOUS' AND isFalsePositive = 0"
    )
    fun observeSuspiciousCount(): Flow<Int>

    @Query("UPDATE quarantine_messages SET isFalsePositive = 1 WHERE id = :id")
    suspend fun markFalsePositive(id: Long)
}

@Dao
interface WhitelistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WhitelistEntity)

    @Delete
    suspend fun delete(entity: WhitelistEntity)

    @Query("SELECT * FROM whitelist_senders ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WhitelistEntity>>

    @Query("SELECT normalizedSender FROM whitelist_senders")
    suspend fun getAllNormalizedSenders(): List<String>
}

@Dao
interface CustomRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomRuleEntity): Long

    @Delete
    suspend fun delete(entity: CustomRuleEntity)

    @Query("SELECT * FROM custom_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<CustomRuleEntity>>

    @Query("SELECT * FROM custom_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<CustomRuleEntity>
}
