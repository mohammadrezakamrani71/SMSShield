package com.smsshield.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smsshield.app.SmsShieldApplication
import com.smsshield.app.data.database.QuarantineEntity
import com.smsshield.app.data.database.QuarantineFeedbackState
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
    private val dismissalTracker = PendingDismissalTracker()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val sbnKey = sbn.key
        val sbnPostTime = sbn.postTime
        val capturedToken = dismissalTracker.recordNotification(sbnKey)

        val parsed = NotificationParser.parse(sbn) ?: return

        val app = application as? SmsShieldApplication ?: return
        val repository = app.repository
        val preferences = app.preferences

        serviceScope.launch {
            val contentHash = DeduplicationHelper.buildContentHash(
                packageName = parsed.packageName,
                sender = parsed.sender,
                body = parsed.body
            )

            if (repository.isDuplicateWithinWindow(contentHash, parsed.timestamp)) {
                return@launch
            }

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
            val blockedSenders = repository.getBlockedSenderRules()
            val customRules = repository.getEnabledCustomRules()
            val learnedModel = repository.getLearnedModelSnapshot()
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
                spamThreshold = spamThreshold,
                blockedSenders = blockedSenders,
                learnedModel = learnedModel
            )

            if (result.verdict == SmsVerdict.SAFE) {
                return@launch
            }

            repository.recordMatchHits(
                matchedCustomKeywords = result.matchedCustomKeywords,
                matchedBlockedSenderPattern = result.matchedBlockedSenderPattern
            )

            val explicitlyBlockedBySender = result.matchedBlockedSenderPattern != null
            val wantsDismiss = isActiveMode &&
                result.verdict == SmsVerdict.SPAM &&
                (!parsed.isPossiblyTruncated || explicitlyBlockedBySender)

            val shouldDismiss = if (wantsDismiss) {
                val stillLatest = dismissalTracker.isStillLatest(sbnKey, capturedToken)
                val stillActiveWithSamePostTime = runCatching {
                    activeNotifications?.any {
                        it.key == sbnKey && it.postTime == sbnPostTime
                    } == true
                }.getOrDefault(false)
                stillLatest && stillActiveWithSamePostTime
            } else {
                false
            }

            val reasonsJson = JSONArray(result.reasons).toString()

            val entity = QuarantineEntity(
                dedupKey = dedupKey,
                contentHash = contentHash,
                packageName = parsed.packageName,
                sender = parsed.sender,
                body = parsed.body,
                score = result.score,
                verdict = result.verdict.name,
                reasonsJson = reasonsJson,
                timestamp = parsed.timestamp,
                wasDismissed = shouldDismiss,
                isFalsePositive = false,
                feedbackState = QuarantineFeedbackState.UNREVIEWED
            )

            repository.insertQuarantine(entity)

            if (shouldDismiss) {
                cancelNotification(sbnKey)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val key = sbn?.key ?: return
        dismissalTracker.clear(key)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
