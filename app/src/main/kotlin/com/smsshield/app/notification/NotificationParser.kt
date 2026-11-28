package com.smsshield.app.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

data class ParsedSmsNotification(
    val packageName: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isPossiblyTruncated: Boolean
)

object NotificationParser {

    val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging",
        "com.oneplus.mms",
        "com.motorola.messaging",
        "com.sonyericsson.conversations",
        "com.miui.mms",
        "org.fossify.messages",
        "com.simplemobiletools.smsmessenger",
        "com.moez.QKSMS",
        "com.textra",
        "xyz.klinker.messenger"
    )

    val TARGET_SMS_PACKAGES: Set<String>
        get() = SMS_PACKAGES

    fun parse(sbn: StatusBarNotification): ParsedSmsNotification? {
        val packageName = sbn.packageName ?: return null
        if (packageName !in SMS_PACKAGES) return null

        val notification = sbn.notification ?: return null
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return null
        }

        val extras = notification.extras ?: return null
        val fallbackSender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            .orEmpty()

        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
        val latestMessage = messagingStyle?.messages?.maxByOrNull { it.timestamp }
            ?: messagingStyle?.messages?.lastOrNull()

        if (latestMessage != null) {
            val messageText = latestMessage.text?.toString()?.trim().orEmpty()
            if (messageText.isNotEmpty()) {
                val sender = latestMessage.person?.name?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: fallbackSender
                val timestamp = if (latestMessage.timestamp > 0L) {
                    latestMessage.timestamp
                } else {
                    sbn.postTime
                }
                return ParsedSmsNotification(
                    packageName = packageName,
                    sender = sender,
                    body = messageText,
                    timestamp = timestamp,
                    isPossiblyTruncated = isPossiblyTruncated(messageText)
                )
            }
        }

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (bigText.isNotEmpty()) {
            return ParsedSmsNotification(
                packageName = packageName,
                sender = fallbackSender,
                body = bigText,
                timestamp = sbn.postTime,
                isPossiblyTruncated = isPossiblyTruncated(bigText)
            )
        }

        val extraText = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (extraText.isNotEmpty()) {
            return ParsedSmsNotification(
                packageName = packageName,
                sender = fallbackSender,
                body = extraText,
                timestamp = sbn.postTime,
                isPossiblyTruncated = isPossiblyTruncated(extraText)
            )
        }

        return null
    }

    private fun isPossiblyTruncated(text: String): Boolean =
        text.endsWith("…") || text.endsWith("...")
}
