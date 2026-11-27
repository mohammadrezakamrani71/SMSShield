package com.smsshield.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smsshield.app.SmsShieldApplication
import com.smsshield.app.data.database.QuarantineEntity
import com.smsshield.engine.SpamClassifier
import com.smsshield.engine.model.SmsInput
import com.smsshield.engine.model.SmsVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

class SmsNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val classifier = SpamClassifier()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val parsed = NotificationParser.parse(sbn) ?: return

        val app = application as? SmsShieldApplication ?: return
        val repository = app.repository
        val preferences = app.preferences

        serviceScope.launch {
            val dedupKey = DeduplicationHelper.buildDedupKey(
                packageName = parsed.packageName,
                sender = parsed.sender,
                body = parsed.body,
                timestamp = parsed.timestamp
            )

            if (repository.existsByDedupKey(dedupKey)) {
                return@launch
            }

            val whitelistedSenders = repository.getWhitelistedSenderSet()
            val customRules = repository.getEnabledCustomRules()
            val spamThreshold = preferences.getSpamThreshold()
            val isActiveMode = preferences.isActiveMode()

            val result = classifier.classify(
                input = SmsInput(
                    sender = parsed.sender,
                    body = parsed.body,
                    isPossiblyTruncated = parsed.isPossiblyTruncated
                ),
                whitelistedSenders = whitelistedSenders,
                customRules = customRules,
                spamThreshold = spamThreshold
            )

            if (result.verdict == SmsVerdict.SAFE) {
                return@launch
            }

            val shouldDismiss = isActiveMode &&
                result.verdict == SmsVerdict.SPAM &&
                !parsed.isPossiblyTruncated

            val reasonsJson = JSONArray(result.reasons).toString()

            val entity = QuarantineEntity(
                dedupKey = dedupKey,
                packageName = parsed.packageName,
                sender = parsed.sender,
                body = parsed.body,
                score = result.score,
                verdict = result.verdict.name,
                reasonsJson = reasonsJson,
                timestamp = parsed.timestamp,
                wasDismissed = shouldDismiss,
                isFalsePositive = false
            )

            repository.insertQuarantine(entity)

            if (shouldDismiss) {
                cancelNotification(sbn.key)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
