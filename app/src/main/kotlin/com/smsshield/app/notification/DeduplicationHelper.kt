package com.smsshield.app.notification

import com.smsshield.engine.PersianNormalizer
import java.security.MessageDigest

object DeduplicationHelper {

    /**
     * Uses the message timestamp exactly rather than a coarse time bucket.
     * This avoids treating two distinct messages near a five-minute boundary
     * as duplicates. The parser should prefer MessagingStyle's message timestamp
     * and use notification post time only as a fallback.
     */
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

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
