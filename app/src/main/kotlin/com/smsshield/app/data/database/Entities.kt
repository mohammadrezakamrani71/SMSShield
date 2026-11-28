package com.smsshield.app.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object QuarantineFeedbackState {
    const val UNREVIEWED = "UNREVIEWED"
    const val CONFIRMED_SPAM = "CONFIRMED_SPAM"
    const val FALSE_POSITIVE = "FALSE_POSITIVE"
}

object TrainingSampleSource {
    const val SHARED = "SHARED"
    const val MANUAL = "MANUAL"
    const val QUARANTINE_CONFIRM = "QUARANTINE_CONFIRM"
    const val QUARANTINE_FALSE_POSITIVE = "QUARANTINE_FALSE_POSITIVE"
}

@Entity(
    tableName = "quarantine_messages",
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["contentHash", "timestamp"])
    ]
)
data class QuarantineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dedupKey: String,
    val contentHash: String = "",
    val packageName: String,
    val sender: String,
    val body: String,
    val score: Int,
    val verdict: String,
    val reasonsJson: String,
    val timestamp: Long,
    val wasDismissed: Boolean,
    val isFalsePositive: Boolean = false,
    val feedbackState: String = QuarantineFeedbackState.UNREVIEWED
)

@Entity(tableName = "whitelist_senders")
data class WhitelistEntity(
    @PrimaryKey
    val normalizedSender: String,
    val displayName: String,
    val addedAt: Long
)

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val weight: Int,
    val isEnabled: Boolean = true,
    val hitCount: Int = 0
)

@Entity(
    tableName = "blocked_senders",
    indices = [Index(value = ["normalizedPattern"], unique = true)]
)
data class BlockedSenderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedPattern: String,
    val displayPattern: String,
    val matchType: String,
    val hitCount: Int = 0,
    val addedAt: Long
)

@Entity(
    tableName = "training_samples",
    indices = [Index(value = ["contentHash"], unique = true)]
)
data class TrainingSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentHash: String,
    val sender: String = "",
    val body: String,
    val label: String,
    val source: String,
    val createdAt: Long
)

@Entity(tableName = "learned_features")
data class LearnedFeatureEntity(
    @PrimaryKey
    val featureKey: String,
    val featureType: String,
    val spamDocCount: Int,
    val safeDocCount: Int,
    val weight: Double,
    val updatedAt: Long
)
