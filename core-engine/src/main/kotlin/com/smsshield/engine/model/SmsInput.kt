package com.smsshield.engine.model

data class SmsInput(
    val sender: String,
    val body: String,
    val isPossiblyTruncated: Boolean = false
)
