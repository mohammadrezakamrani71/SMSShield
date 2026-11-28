package com.smsshield.app.data.database

import com.smsshield.app.notification.DeduplicationHelper
import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.CustomKeywordRule
import com.smsshield.engine.FullBackupData
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.PersianNormalizer
import com.smsshield.engine.RulePackCodec
import com.smsshield.engine.TrainingSampleBackupItem
import com.smsshield.engine.learning.AdaptiveSpamLearner
import com.smsshield.engine.learning.FeatureType
import com.smsshield.engine.learning.LearnedFeature
import com.smsshield.engine.learning.LearnedModelSnapshot
import com.smsshield.engine.learning.LearningReport
import com.smsshield.engine.learning.SampleLabel
import com.smsshield.engine.learning.TrainingSampleInput
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
    val addedTrainingSamples: Int,
    val restoredThreshold: Int,
    val restoredThemeMode: ThemeMode
)

class ShieldRepository(
    private val database: AppDatabase
) {

    private val quarantineDao = database.quarantineDao()
    private val whitelistDao = database.whitelistDao()
    private val customRuleDao = database.customRuleDao()
    private val blockedSenderDao = database.blockedSenderDao()
    private val trainingSampleDao = database.trainingSampleDao()
    private val learnedFeatureDao = database.learnedFeatureDao()

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

    suspend fun markFalsePositive(id: Long) {
        setQuarantineFeedback(id, QuarantineFeedbackState.FALSE_POSITIVE)
    }

    suspend fun setQuarantineFeedback(id: Long, newState: String): LearningReport? {
        val item = quarantineDao.getById(id) ?: return null
        val isFalsePos = newState == QuarantineFeedbackState.FALSE_POSITIVE
        quarantineDao.updateFeedbackState(id, newState, isFalsePos)

        val sampleHash = buildTrainingSampleHash(item.sender, item.body)
        when (newState) {
            QuarantineFeedbackState.CONFIRMED_SPAM -> {
                trainingSampleDao.upsert(
                    TrainingSampleEntity(
                        contentHash = sampleHash,
                        sender = item.sender,
                        body = item.body,
                        label = SampleLabel.SPAM.name,
                        source = TrainingSampleSource.QUARANTINE_CONFIRM,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            QuarantineFeedbackState.FALSE_POSITIVE -> {
                trainingSampleDao.upsert(
                    TrainingSampleEntity(
                        contentHash = sampleHash,
                        sender = item.sender,
                        body = item.body,
                        label = SampleLabel.SAFE.name,
                        source = TrainingSampleSource.QUARANTINE_FALSE_POSITIVE,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            else -> {
                trainingSampleDao.deleteByContentHash(sampleHash)
            }
        }
        return retrainAdaptiveModel()
    }

    suspend fun deleteQuarantineMessageById(id: Long) =
        quarantineDao.deleteById(id)

    suspend fun clearAllQuarantine() =
        quarantineDao.deleteAll()

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

    // Smart Learning Samples & Model Persistence
    fun observeTrainingSamples(): Flow<List<TrainingSampleEntity>> =
        trainingSampleDao.observeAll()

    fun observeLearnedFeatures(): Flow<List<LearnedFeatureEntity>> =
        learnedFeatureDao.observeAll()

    suspend fun addTrainingSamples(
        bodies: List<String>,
        sender: String = "",
        label: SampleLabel,
        source: String
    ): Int {
        var added = 0
        val now = System.currentTimeMillis()
        for (rawBody in bodies) {
            val cleanedBody = rawBody.trim()
            if (cleanedBody.isEmpty()) continue
            val hash = buildTrainingSampleHash(sender, cleanedBody)
            trainingSampleDao.upsert(
                TrainingSampleEntity(
                    contentHash = hash,
                    sender = sender.trim(),
                    body = cleanedBody,
                    label = label.name,
                    source = source,
                    createdAt = now + added
                )
            )
            added++
        }
        return added
    }

    suspend fun deleteTrainingSample(id: Long): LearningReport {
        trainingSampleDao.deleteById(id)
        return retrainAdaptiveModel()
    }

    suspend fun retrainAdaptiveModel(): LearningReport {
        val samples = trainingSampleDao.getAll().map { entity ->
            val label = runCatching { SampleLabel.valueOf(entity.label) }
                .getOrDefault(SampleLabel.SPAM)
            TrainingSampleInput(
                sender = entity.sender,
                body = entity.body,
                label = label
            )
        }
        val existingKeywords = customRuleDao.getAllRules().map { it.keyword }.toSet()
        val (snapshot, report) = AdaptiveSpamLearner.train(samples, existingKeywords)

        val now = System.currentTimeMillis()
        val featureEntities = snapshot.features.values.map { feature ->
            LearnedFeatureEntity(
                featureKey = feature.key,
                featureType = feature.type.name,
                spamDocCount = feature.spamDocCount,
                safeDocCount = feature.safeDocCount,
                weight = feature.weight,
                updatedAt = now
            )
        }
        learnedFeatureDao.replaceAll(featureEntities)
        return report
    }

    suspend fun getLearnedModelSnapshot(): LearnedModelSnapshot? {
        val samples = trainingSampleDao.getAll()
        if (samples.isEmpty()) return null
        val totalSpam = samples.count { it.label == SampleLabel.SPAM.name }
        val totalSafe = samples.count { it.label == SampleLabel.SAFE.name }

        val storedFeatures = learnedFeatureDao.getAll()
        if (storedFeatures.isEmpty()) {
            val existingKeywords = customRuleDao.getAllRules().map { it.keyword }.toSet()
            val inputs = samples.map {
                TrainingSampleInput(
                    sender = it.sender,
                    body = it.body,
                    label = runCatching { SampleLabel.valueOf(it.label) }.getOrDefault(SampleLabel.SPAM)
                )
            }
            return AdaptiveSpamLearner.train(inputs, existingKeywords).first
        }

        val featureMap = storedFeatures.associate { entity ->
            val type = runCatching { FeatureType.valueOf(entity.featureType) }
                .getOrDefault(FeatureType.UNIGRAM)
            entity.featureKey to LearnedFeature(
                key = entity.featureKey,
                type = type,
                spamDocCount = entity.spamDocCount,
                safeDocCount = entity.safeDocCount,
                weight = entity.weight
            )
        }
        return LearnedModelSnapshot(
            totalSpamSamples = totalSpam,
            totalSafeSamples = totalSafe,
            features = featureMap
        )
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

    suspend fun exportFullBackup(
        spamThreshold: Int,
        themeMode: ThemeMode = ThemeMode.SYSTEM
    ): FullBackupData {
        val rules = customRuleDao.getAllRules().map {
            KeywordRuleItem(keyword = it.keyword, weight = it.weight)
        }
        val blocked = blockedSenderDao.getAll().map {
            val matchType = runCatching { SenderMatchType.valueOf(it.matchType) }
                .getOrDefault(SenderMatchType.EXACT)
            BlockedSenderItem(pattern = it.displayPattern, matchType = matchType)
        }
        val whitelist = whitelistDao.getAll().map { it.normalizedSender }
        val samples = trainingSampleDao.getAll().map {
            TrainingSampleBackupItem(
                sender = it.sender,
                body = it.body,
                label = it.label,
                source = it.source
            )
        }

        return FullBackupData(
            schemaVersion = 2,
            exportedAt = System.currentTimeMillis(),
            spamThreshold = spamThreshold.coerceIn(50, 100),
            customRules = rules,
            blockedSenders = blocked,
            whitelistSenders = whitelist,
            themeMode = themeMode.name,
            trainingSamples = samples
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

        val existingSampleHashes = trainingSampleDao.getAll().map { it.contentHash }.toMutableSet()
        var addedSamples = 0
        for (sample in backup.trainingSamples) {
            val hash = buildTrainingSampleHash(sample.sender, sample.body)
            if (!existingSampleHashes.add(hash)) continue
            val label = runCatching { SampleLabel.valueOf(sample.label) }.getOrDefault(SampleLabel.SPAM)
            addTrainingSamples(
                bodies = listOf(sample.body),
                sender = sample.sender,
                label = label,
                source = sample.source
            )
            addedSamples++
        }
        if (addedSamples > 0) {
            retrainAdaptiveModel()
        }

        val themeMode = runCatching { ThemeMode.valueOf(backup.themeMode) }
            .getOrDefault(ThemeMode.SYSTEM)

        return BackupRestoreSummary(
            addedKeywords = packResult.addedKeywords,
            addedBlockedSenders = packResult.addedBlockedSenders,
            addedWhitelist = addedWhitelist,
            addedTrainingSamples = addedSamples,
            restoredThreshold = backup.spamThreshold.coerceIn(50, 100),
            restoredThemeMode = themeMode
        )
    }

    private fun buildTrainingSampleHash(sender: String, body: String): String {
        return DeduplicationHelper.buildContentHash(
            packageName = "training_sample",
            sender = sender,
            body = body
        )
    }
}
