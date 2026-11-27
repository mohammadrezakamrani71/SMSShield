package com.smsshield.engine.model

data class ClassificationResult(
    val verdict: SmsVerdict,
    val score: Int,
    val reasons: List<String>
)
