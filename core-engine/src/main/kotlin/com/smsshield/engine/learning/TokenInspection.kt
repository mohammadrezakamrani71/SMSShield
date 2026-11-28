package com.smsshield.engine.learning

enum class TokenSentiment {
    SPAM_LEANING,
    SAFE_LEANING,
    NEUTRAL
}

data class InspectedToken(
    val token: String,
    val sentiment: TokenSentiment,
    val weight: Double,
    val isAlreadyCustomRule: Boolean
)
