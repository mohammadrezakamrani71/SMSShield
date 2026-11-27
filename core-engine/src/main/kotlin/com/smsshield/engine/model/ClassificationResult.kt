package com.smsshield.engine.model

data class ClassificationResult(
    val verdict: SmsVerdict,
    val score: Int,
    val reasons: List<String>,
    val matchedCustomKeywords: List<String> = emptyList(),
    val matchedBlockedSenderPattern: String? = null
)
