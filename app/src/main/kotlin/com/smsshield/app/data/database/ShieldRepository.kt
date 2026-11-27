package com.smsshield.app.data.database

import com.smsshield.app.notification.DeduplicationHelper
import com.smsshield.engine.CustomKeywordRule
import com.smsshield.engine.PersianNormalizer
import kotlinx.coroutines.flow.Flow

class ShieldRepository(
    private val database: AppDatabase
) {

    private val quarantineDao = database.quarantineDao()
    private val whitelistDao = database.whitelistDao()
    private val customRuleDao = database.customRuleDao()

    fun observeQuarantineMessages(): Flow<List<QuarantineEntity>> =
        quarantineDao.observeAll()

    fun observeBlockedTodayCount(startOfDayMillis: Long): Flow<Int> =
        quarantineDao.observeBlockedTodayCount(startOfDayMillis)

    fun observeTotalBlockedCount(): Flow<Int> =
        quarantineDao.observeTotalBlockedCount()

    fun observeSuspiciousCount(): Flow<Int> =
        quarantineDao.observeSuspiciousCount()

    suspend fun existsByDedupKey(dedupKey: String): Boolean =
        quarantineDao.existsByDedupKey(dedupKey)

    suspend fun isDuplicateWithinWindow(
        contentHash: String,
        timestamp: Long,
        windowMillis: Long = DeduplicationHelper.SLIDING_WINDOW_MILLIS
    ): Boolean = quarantineDao.existsRecentByContentHash(
        contentHash = contentHash,
        minTimestamp = timestamp - windowMillis
    )

    suspend fun insertQuarantine(entity: QuarantineEntity): Long =
        quarantineDao.insert(entity)

    suspend fun markFalsePositive(id: Long) =
        quarantineDao.markFalsePositive(id)

    fun observeWhitelistedSenders(): Flow<List<WhitelistEntity>> =
        whitelistDao.observeAll()

    suspend fun getWhitelistedSenderSet(): Set<String> =
        whitelistDao.getAllNormalizedSenders().toSet()

    suspend fun addWhitelistedSender(sender: String, displayName: String = sender) {
        val normalized = PersianNormalizer.normalizeSender(sender)
        if (normalized.isEmpty()) return
        whitelistDao.upsert(
            WhitelistEntity(
                normalizedSender = normalized,
                displayName = displayName.trim().ifEmpty { normalized },
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteWhitelistedSender(entity: WhitelistEntity) =
        whitelistDao.delete(entity)

    fun observeCustomRules(): Flow<List<CustomRuleEntity>> =
        customRuleDao.observeAll()

    suspend fun getEnabledCustomRules(): List<CustomKeywordRule> =
        customRuleDao.getEnabledRules().map {
            CustomKeywordRule(
                keyword = it.keyword,
                weight = it.weight,
                isEnabled = it.isEnabled
            )
        }

    suspend fun addCustomRule(keyword: String, weight: Int) {
        val cleaned = keyword.trim()
        if (cleaned.isEmpty()) return
        customRuleDao.insert(
            CustomRuleEntity(
                keyword = cleaned,
                weight = weight.coerceIn(-50, 100),
                isEnabled = true
            )
        )
    }

    suspend fun deleteCustomRule(entity: CustomRuleEntity) =
        customRuleDao.delete(entity)
}
