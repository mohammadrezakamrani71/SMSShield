package com.smsshield.app.notification

import com.smsshield.engine.PersianNormalizer
import java.security.MessageDigest
import kotlin.math.abs

object DeduplicationHelper {

    const val SLIDING_WINDOW_MILLIS = 60_000L

    fun buildContentHash(
        packageName: String,
        sender: String,
        body: String
    ): String {
        val normalizedSender = PersianNormalizer.normalizeSender(sender)
        val normalizedBody = PersianNormalizer.normalize(body)
        val raw = "$packageName|$normalizedSender|$normalizedBody"
        return sha256Hex(raw)
    }

    fun buildDedupKey(
        packageName: String,
        sender: String,
        body: String,
        timestamp: Long
    ): String {
        val normalizedSender = PersianNormalizer.normalizeSender(sender)
        val normalizedBody = PersianNormalizer.normalize(body)
        val raw = "$packageName|$normalizedSender|$normalizedBody|$timestamp"
        return sha256Hex(raw)
    }

    fun isWithinSlidingWindow(
        existingTimestamp: Long,
        newTimestamp: Long,
        windowMillis: Long = SLIDING_WINDOW_MILLIS
    ): Boolean {
        return abs(newTimestamp - existingTimestamp) <= windowMillis
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
