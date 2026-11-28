package com.smsshield.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smsshield.app.data.database.BackupRestoreSummary
import com.smsshield.app.data.database.BlockedSenderEntity
import com.smsshield.app.data.database.CustomRuleEntity
import com.smsshield.app.data.database.LearnedFeatureEntity
import com.smsshield.app.data.database.ListenerDiagnostics
import com.smsshield.app.data.database.PackImportSummary
import com.smsshield.app.data.database.QuarantineEntity
import com.smsshield.app.data.database.QuarantineFeedbackState
import com.smsshield.app.data.database.ShieldPreferences
import com.smsshield.app.data.database.ShieldRepository
import com.smsshield.app.data.database.ThemeMode
import com.smsshield.app.data.database.TrainingSampleEntity
import com.smsshield.app.data.database.WhitelistEntity
import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.RulePackCodec
import com.smsshield.engine.SpamClassifier
import com.smsshield.engine.learning.AdaptiveSpamLearner
import com.smsshield.engine.learning.InspectedToken
import com.smsshield.engine.learning.LearningReport
import com.smsshield.engine.learning.SampleLabel
import com.smsshield.engine.learning.TrainingSampleInput
import com.smsshield.engine.model.ClassificationResult
import com.smsshield.engine.model.SenderMatchType
import com.smsshield.engine.model.SmsInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ShieldViewModel(
    private val repository: ShieldRepository,
    private val preferences: ShieldPreferences
) : ViewModel() {

    private val classifier = SpamClassifier()

    private val startOfTodayMillis: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val isActiveMode: StateFlow<Boolean> = preferences.isActiveModeFlow
    val spamThreshold: StateFlow<Int> = preferences.spamThresholdFlow
    val themeMode: StateFlow<ThemeMode> = preferences.themeModeFlow
    val diagnostics: StateFlow<ListenerDiagnostics> = preferences.diagnosticsFlow

    val blockedTodayCount: StateFlow<Int> = repository
        .observeBlockedTodayCount(startOfTodayMillis)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalBlockedCount: StateFlow<Int> = repository
        .observeTotalBlockedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val suspiciousCount: StateFlow<Int> = repository
        .observeSuspiciousCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val quarantineMessages: StateFlow<List<QuarantineEntity>> = repository
        .observeQuarantineMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val whitelistedSenders: StateFlow<List<WhitelistEntity>> = repository
        .observeWhitelistedSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockedSenders: StateFlow<List<BlockedSenderEntity>> = repository
        .observeBlockedSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customRules: StateFlow<List<CustomRuleEntity>> = repository
        .observeCustomRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalUserHits: StateFlow<Int> = repository
        .observeTotalUserHits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val trainingSamples: StateFlow<List<TrainingSampleEntity>> = repository
        .observeTrainingSamples()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val learnedFeatures: StateFlow<List<LearnedFeatureEntity>> = repository
        .observeLearnedFeatures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _learningReport = MutableStateFlow<LearningReport?>(null)
    val learningReport: StateFlow<LearningReport?> = _learningReport.asStateFlow()

    init {
        viewModelScope.launch {
            _learningReport.value = repository.retrainAdaptiveModel()
        }
    }

    fun setActiveMode(active: Boolean) {
        preferences.setActiveMode(active)
    }

    fun setSpamThreshold(threshold: Int) {
        preferences.setSpamThreshold(threshold)
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun toggleQuarantineFeedback(message: QuarantineEntity, targetState: String) {
        val nextState = if (message.feedbackState == targetState) {
            QuarantineFeedbackState.UNREVIEWED
        } else {
            targetState
        }
        viewModelScope.launch {
            val report = repository.setQuarantineFeedback(message.id, nextState)
            if (report != null) {
                _learningReport.value = report
            }
        }
    }

    fun deleteQuarantineMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteQuarantineMessageById(messageId)
        }
    }

    fun clearAllQuarantine() {
        viewModelScope.launch {
            repository.clearAllQuarantine()
        }
    }

    fun whitelistSender(sender: String, displayName: String = sender) {
        viewModelScope.launch {
            repository.addWhitelistedSender(sender, displayName)
        }
    }

    fun removeWhitelistedSender(entity: WhitelistEntity) {
        viewModelScope.launch {
            repository.deleteWhitelistedSender(entity)
        }
    }

    fun addBlockedSender(pattern: String, matchType: SenderMatchType = SenderMatchType.EXACT) {
        viewModelScope.launch {
            repository.addBlockedSender(pattern, matchType)
        }
    }

    fun removeBlockedSender(entity: BlockedSenderEntity) {
        viewModelScope.launch {
            repository.deleteBlockedSender(entity)
        }
    }

    fun addCustomRule(keyword: String, weight: Int) {
        viewModelScope.launch {
            repository.addCustomRule(keyword, weight)
            if (_learningReport.value != null) {
                _learningReport.value = repository.retrainAdaptiveModel()
            }
        }
    }

    fun addCustomRulesBatch(keywords: List<String>, weight: Int) {
        viewModelScope.launch {
            for (keyword in keywords) {
                repository.addCustomRule(keyword, weight)
            }
            _learningReport.value = repository.retrainAdaptiveModel()
        }
    }

    fun removeCustomRule(entity: CustomRuleEntity) {
        viewModelScope.launch {
            repository.deleteCustomRule(entity)
        }
    }

    suspend fun simulateClassification(sender: String, body: String): ClassificationResult {
        val whitelistedSenders = repository.getWhitelistedSenderSet()
        val blockedSenders = repository.getBlockedSenderRules()
        val customRules = repository.getEnabledCustomRules()
        val learnedModel = repository.getLearnedModelSnapshot()
        val threshold = preferences.getSpamThreshold()

        return classifier.classify(
            input = SmsInput(sender = sender.trim(), body = body),
            whitelistedSenders = whitelistedSenders,
            customRules = customRules,
            spamThreshold = threshold,
            blockedSenders = blockedSenders,
            learnedModel = learnedModel
        )
    }

    suspend fun inspectMessageTokens(body: String): List<InspectedToken> {
        val learnedModel = repository.getLearnedModelSnapshot()
        val customRules = repository.getEnabledCustomRules()
        return AdaptiveSpamLearner.inspectMessageTokens(
            body = body,
            model = learnedModel,
            customRules = customRules
        )
    }

    fun isLikelyMixedSender(sender: String): Boolean {
        val sampleInputs = trainingSamples.value.map { entity ->
            TrainingSampleInput(
                sender = entity.sender,
                body = entity.body,
                label = runCatching { SampleLabel.valueOf(entity.label) }.getOrDefault(SampleLabel.SPAM)
            )
        }
        return AdaptiveSpamLearner.isLikelyMixedSender(sender, sampleInputs)
    }

    suspend fun addTrainingSamples(
        bodies: List<String>,
        sender: String,
        label: SampleLabel,
        source: String
    ): Int {
        val added = repository.addTrainingSamples(
            bodies = bodies,
            sender = sender,
            label = label,
            source = source
        )
        _learningReport.value = repository.retrainAdaptiveModel()
        return added
    }

    fun deleteTrainingSample(id: Long) {
        viewModelScope.launch {
            _learningReport.value = repository.deleteTrainingSample(id)
        }
    }

    suspend fun runSmartTraining(): LearningReport {
        val report = repository.retrainAdaptiveModel()
        _learningReport.value = report
        return report
    }

    suspend fun exportBackupJson(): String {
        val data = repository.exportFullBackup(
            spamThreshold = preferences.getSpamThreshold(),
            themeMode = preferences.getThemeMode()
        )
        return RulePackCodec.encodeBackupToJson(data)
    }

    suspend fun importBackupJson(rawJson: String): Result<BackupRestoreSummary> {
        return RulePackCodec.decodeBackupFromJson(rawJson).mapCatching { backup ->
            val summary = repository.restoreFullBackup(backup)
            preferences.setSpamThreshold(summary.restoredThreshold)
            preferences.setThemeMode(summary.restoredThemeMode)
            _learningReport.value = repository.retrainAdaptiveModel()
            summary
        }
    }

    suspend fun importSelectedPackItems(
        keywords: List<KeywordRuleItem>,
        blockedSenders: List<BlockedSenderItem>
    ): PackImportSummary {
        return repository.importSelectedPackItems(keywords, blockedSenders)
    }

    class Factory(
        private val repository: ShieldRepository,
        private val preferences: ShieldPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShieldViewModel(repository, preferences) as T
        }
    }
}
