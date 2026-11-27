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

    @Query(
        "SELECT EXISTS(SELECT 1 FROM quarantine_messages " +
            "WHERE contentHash = :contentHash AND timestamp >= :minTimestamp)"
    )
    suspend fun existsRecentByContentHash(contentHash: String, minTimestamp: Long): Boolean

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

    @Query("DELETE FROM whitelist_senders WHERE normalizedSender = :normalizedSender")
    suspend fun deleteByNormalizedSender(normalizedSender: String)

    @Query("SELECT * FROM whitelist_senders ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WhitelistEntity>>

    @Query("SELECT * FROM whitelist_senders")
    suspend fun getAll(): List<WhitelistEntity>

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

    @Query("SELECT * FROM custom_rules")
    suspend fun getAllRules(): List<CustomRuleEntity>

    @Query("SELECT * FROM custom_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<CustomRuleEntity>

    @Query("UPDATE custom_rules SET hitCount = hitCount + 1 WHERE keyword = :keyword")
    suspend fun incrementHitCountByKeyword(keyword: String)

    @Query("SELECT COALESCE(SUM(hitCount), 0) FROM custom_rules")
    fun observeTotalHits(): Flow<Int>
}

@Dao
interface BlockedSenderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BlockedSenderEntity): Long

    @Delete
    suspend fun delete(entity: BlockedSenderEntity)

    @Query("DELETE FROM blocked_senders WHERE normalizedPattern = :normalizedPattern")
    suspend fun deleteByNormalizedPattern(normalizedPattern: String)

    @Query("SELECT * FROM blocked_senders ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BlockedSenderEntity>>

    @Query("SELECT * FROM blocked_senders")
    suspend fun getAll(): List<BlockedSenderEntity>

    @Query("UPDATE blocked_senders SET hitCount = hitCount + 1 WHERE normalizedPattern = :normalizedPattern")
    suspend fun incrementHitCount(normalizedPattern: String)

    @Query("SELECT COALESCE(SUM(hitCount), 0) FROM blocked_senders")
    fun observeTotalHits(): Flow<Int>
}
