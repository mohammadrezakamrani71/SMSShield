package com.smsshield.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class SenderMatchType {
    EXACT,
    PREFIX
}

data class BlockedSenderRule(
    val pattern: String,
    val matchType: SenderMatchType = SenderMatchType.EXACT
)
