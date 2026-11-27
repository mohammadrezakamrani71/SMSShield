package com.smsshield.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smsshield.app.data.database.BackupRestoreSummary
import com.smsshield.app.data.database.BlockedSenderEntity
import com.smsshield.app.data.database.CustomRuleEntity
import com.smsshield.app.data.database.PackImportSummary
import com.smsshield.app.data.database.QuarantineEntity
import com.smsshield.app.data.database.ShieldPreferences
import com.smsshield.app.data.database.ShieldRepository
import com.smsshield.app.data.database.WhitelistEntity
import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.RulePackCodec
import com.smsshield.engine.model.SenderMatchType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ShieldViewModel(
    private val repository: ShieldRepository,
    private val preferences: ShieldPreferences
) : ViewModel() {

    private val startOfTodayMillis: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val isActiveMode: StateFlow<Boolean> = preferences.isActiveModeFlow
    val spamThreshold: StateFlow<Int> = preferences.spamThresholdFlow

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

    fun setActiveMode(active: Boolean) {
        preferences.setActiveMode(active)
    }

    fun setSpamThreshold(threshold: Int) {
        preferences.setSpamThreshold(threshold)
    }

    fun markNotSpam(messageId: Long) {
        viewModelScope.launch {
            repository.markFalsePositive(messageId)
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
        }
    }

    fun removeCustomRule(entity: CustomRuleEntity) {
        viewModelScope.launch {
            repository.deleteCustomRule(entity)
        }
    }

    suspend fun exportBackupJson(): String {
        val data = repository.exportFullBackup(preferences.getSpamThreshold())
        return RulePackCodec.encodeBackupToJson(data)
    }

    suspend fun importBackupJson(rawJson: String): Result<BackupRestoreSummary> {
        return RulePackCodec.decodeBackupFromJson(rawJson).mapCatching { backup ->
            val summary = repository.restoreFullBackup(backup)
            preferences.setSpamThreshold(summary.restoredThreshold)
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
