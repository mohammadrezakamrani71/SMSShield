package com.smsshield.app.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val isFalsePositive: Boolean = false
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
    val isEnabled: Boolean = true
)
