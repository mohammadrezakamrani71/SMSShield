package com.smsshield.app.data.database

import com.smsshield.app.notification.DeduplicationHelper
import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.CustomKeywordRule
import com.smsshield.engine.FullBackupData
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.PersianNormalizer
import com.smsshield.engine.RulePackCodec
import com.smsshield.engine.model.BlockedSenderRule
import com.smsshield.engine.model.SenderMatchType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class PackImportSummary(
    val addedKeywords: Int,
    val addedBlockedSenders: Int
) {
    val totalAdded: Int get() = addedKeywords + addedBlockedSenders
}

data class BackupRestoreSummary(
    val addedKeywords: Int,
    val addedBlockedSenders: Int,
    val addedWhitelist: Int,
    val restoredThreshold: Int
)

class ShieldRepository(
    private val database: AppDatabase
) {

    private val quarantineDao = database.quarantineDao()
    private val whitelistDao = database.whitelistDao()
    private val customRuleDao = database.customRuleDao()
    private val blockedSenderDao = database.blockedSenderDao()

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
        blockedSenderDao.deleteByNormalizedPattern(normalized)
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

    fun observeBlockedSenders(): Flow<List<BlockedSenderEntity>> =
        blockedSenderDao.observeAll()

    suspend fun getBlockedSenderRules(): List<BlockedSenderRule> =
        blockedSenderDao.getAll().map {
            val matchType = runCatching { SenderMatchType.valueOf(it.matchType) }
                .getOrDefault(SenderMatchType.EXACT)
            BlockedSenderRule(pattern = it.normalizedPattern, matchType = matchType)
        }

    suspend fun addBlockedSender(
        pattern: String,
        matchType: SenderMatchType = SenderMatchType.EXACT
    ) {
        val raw = pattern.trim()
        val effectiveMatchType = if (raw.endsWith("*")) SenderMatchType.PREFIX else matchType
        val normalized = PersianNormalizer.normalizeSender(raw.removeSuffix("*"))
        if (normalized.isEmpty()) return
        whitelistDao.deleteByNormalizedSender(normalized)
        val display = if (effectiveMatchType == SenderMatchType.PREFIX && !raw.endsWith("*")) {
            "$normalized*"
        } else {
            raw
        }
        blockedSenderDao.upsert(
            BlockedSenderEntity(
                normalizedPattern = normalized,
                displayPattern = display,
                matchType = effectiveMatchType.name,
                hitCount = 0,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBlockedSender(entity: BlockedSenderEntity) =
        blockedSenderDao.delete(entity)

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
        val normalized = PersianNormalizer.normalize(cleaned)
        if (normalized.isEmpty()) return
        val existingNormalized = customRuleDao.getAllRules()
            .map { PersianNormalizer.normalize(it.keyword) }
            .toSet()
        if (normalized in existingNormalized) return
        customRuleDao.insert(
            CustomRuleEntity(
                keyword = cleaned,
                weight = weight.coerceIn(RulePackCodec.MIN_WEIGHT, RulePackCodec.MAX_WEIGHT),
                isEnabled = true,
                hitCount = 0
            )
        )
    }

    suspend fun deleteCustomRule(entity: CustomRuleEntity) =
        customRuleDao.delete(entity)

    suspend fun recordMatchHits(
        matchedCustomKeywords: List<String>,
        matchedBlockedSenderPattern: String?
    ) {
        for (keyword in matchedCustomKeywords.distinct()) {
            customRuleDao.incrementHitCountByKeyword(keyword)
        }
        if (!matchedBlockedSenderPattern.isNullOrBlank()) {
            blockedSenderDao.incrementHitCount(matchedBlockedSenderPattern)
        }
    }

    fun observeTotalUserHits(): Flow<Int> =
        combine(
            customRuleDao.observeTotalHits(),
            blockedSenderDao.observeTotalHits()
        ) { customHits, blockedHits ->
            customHits + blockedHits
        }

    suspend fun importSelectedPackItems(
        keywords: List<KeywordRuleItem>,
        blockedSenders: List<BlockedSenderItem>
    ): PackImportSummary {
        val existingKeywords = customRuleDao.getAllRules()
            .map { PersianNormalizer.normalize(it.keyword) }
            .toMutableSet()
        var addedKeywords = 0
        for (item in keywords) {
            val cleaned = item.keyword.trim()
            val normalized = PersianNormalizer.normalize(cleaned)
            if (normalized.isEmpty() || !existingKeywords.add(normalized)) continue
            customRuleDao.insert(
                CustomRuleEntity(
                    keyword = cleaned,
                    weight = item.weight.coerceIn(RulePackCodec.MIN_WEIGHT, RulePackCodec.MAX_WEIGHT),
                    isEnabled = true,
                    hitCount = 0
                )
            )
            addedKeywords++
        }

        val existingBlocked = blockedSenderDao.getAll()
            .map { it.normalizedPattern }
            .toMutableSet()
        var addedBlocked = 0
        for (sender in blockedSenders) {
            val normalized = PersianNormalizer.normalizeSender(sender.pattern.trim().removeSuffix("*"))
            if (normalized.isEmpty() || !existingBlocked.add(normalized)) continue
            addBlockedSender(sender.pattern, sender.matchType)
            addedBlocked++
        }

        return PackImportSummary(
            addedKeywords = addedKeywords,
            addedBlockedSenders = addedBlocked
        )
    }

    suspend fun exportFullBackup(spamThreshold: Int): FullBackupData {
        val rules = customRuleDao.getAllRules().map {
            KeywordRuleItem(keyword = it.keyword, weight = it.weight)
        }
        val blocked = blockedSenderDao.getAll().map {
            val matchType = runCatching { SenderMatchType.valueOf(it.matchType) }
                .getOrDefault(SenderMatchType.EXACT)
            BlockedSenderItem(pattern = it.displayPattern, matchType = matchType)
        }
        val whitelist = whitelistDao.getAll().map { it.displayName }

        return FullBackupData(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            spamThreshold = spamThreshold.coerceIn(50, 100),
            customRules = rules,
            blockedSenders = blocked,
            whitelistSenders = whitelist
        )
    }

    suspend fun restoreFullBackup(backup: FullBackupData): BackupRestoreSummary {
        val packResult = importSelectedPackItems(
            keywords = backup.customRules,
            blockedSenders = backup.blockedSenders
        )
        val existingWhitelist = whitelistDao.getAllNormalizedSenders().toMutableSet()
        var addedWhitelist = 0
        for (sender in backup.whitelistSenders) {
            val normalized = PersianNormalizer.normalizeSender(sender)
            if (normalized.isEmpty() || !existingWhitelist.add(normalized)) continue
            addWhitelistedSender(sender, sender)
            addedWhitelist++
        }
        return BackupRestoreSummary(
            addedKeywords = packResult.addedKeywords,
            addedBlockedSenders = packResult.addedBlockedSenders,
            addedWhitelist = addedWhitelist,
            restoredThreshold = backup.spamThreshold.coerceIn(50, 100)
        )
    }
}
