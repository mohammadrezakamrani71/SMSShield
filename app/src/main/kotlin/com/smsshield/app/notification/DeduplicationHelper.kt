package com.smsshield.app.notification

import com.smsshield.engine.PersianNormalizer
import java.security.MessageDigest

object DeduplicationHelper {

    private const val WINDOW_MILLIS = 300_000L

    fun buildDedupKey(
        packageName: String,
        sender: String,
        body: String,
        timestamp: Long
    ): String {
        val normalizedSender = PersianNormalizer.normalizeSender(sender)
        val normalizedBody = PersianNormalizer.normalize(body)
        val timeBucket = timestamp / WINDOW_MILLIS
        val raw = "$packageName|$normalizedSender|$normalizedBody|$timeBucket"
        return sha256Hex(raw)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
