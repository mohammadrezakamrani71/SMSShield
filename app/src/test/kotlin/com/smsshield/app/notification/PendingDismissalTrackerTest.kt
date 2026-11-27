package com.smsshield.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDismissalTrackerTest {

    @Test
    fun `keeps latest token valid until superseded on the same key`() {
        val tracker = PendingDismissalTracker()
        val key = "0|com.google.android.apps.messaging|1|null|1000"

        val olderSpamToken = tracker.recordNotification(key)
        assertTrue(tracker.isStillLatest(key, olderSpamToken))

        val newerSafeToken = tracker.recordNotification(key)
        assertFalse(tracker.isStillLatest(key, olderSpamToken))
        assertTrue(tracker.isStillLatest(key, newerSafeToken))
    }

    @Test
    fun `tracks independent notification keys without interference`() {
        val tracker = PendingDismissalTracker()
        val keyA = "0|com.google.android.apps.messaging|1|null|1000"
        val keyB = "0|com.google.android.apps.messaging|2|null|1000"

        val tokenA = tracker.recordNotification(keyA)
        val tokenB = tracker.recordNotification(keyB)

        assertTrue(tracker.isStillLatest(keyA, tokenA))
        assertTrue(tracker.isStillLatest(keyB, tokenB))
    }

    @Test
    fun `clear removes stale key on notification removal and invalidates in-flight token`() {
        val tracker = PendingDismissalTracker()
        val key = "0|com.google.android.apps.messaging|1|null|1000"

        val token = tracker.recordNotification(key)
        assertTrue(tracker.isStillLatest(key, token))

        tracker.clear(key)
        assertFalse(tracker.isStillLatest(key, token))
    }
}
