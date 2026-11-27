package com.smsshield.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicationHelperTest {

    @Test
    fun `produces deterministic contentHash across Persian and Arabic normalizations`() {
        val hashA = DeduplicationHelper.buildContentHash(
            packageName = "com.google.android.apps.messaging",
            sender = "+98 3000 1234",
            body = "فروش\u200Cويژه كالا با ۵۰٪ تخفيف لغو=۱۱"
        )
        val hashB = DeduplicationHelper.buildContentHash(
            packageName = "com.google.android.apps.messaging",
            sender = "+9830001234",
            body = "  فروش ویژه کالا با 50٪ تخفیف   لغو=11 "
        )

        assertEquals(hashA, hashB)
        assertEquals(64, hashA.length)
    }

    @Test
    fun `produces different contentHash for different packages or bodies`() {
        val hashA = DeduplicationHelper.buildContentHash(
            packageName = "com.google.android.apps.messaging",
            sender = "30001234",
            body = "پیام اول"
        )
        val hashB = DeduplicationHelper.buildContentHash(
            packageName = "com.samsung.android.messaging",
            sender = "30001234",
            body = "پیام اول"
        )
        val hashC = DeduplicationHelper.buildContentHash(
            packageName = "com.google.android.apps.messaging",
            sender = "30001234",
            body = "پیام دوم"
        )

        assertNotEquals(hashA, hashB)
        assertNotEquals(hashA, hashC)
    }

    @Test
    fun `validates 60-second sliding window duplicate boundaries`() {
        val baseTime = 1_700_000_000_000L

        assertTrue(DeduplicationHelper.isWithinSlidingWindow(baseTime, baseTime + 5_000L))
        assertTrue(DeduplicationHelper.isWithinSlidingWindow(baseTime, baseTime + 60_000L))
        assertFalse(DeduplicationHelper.isWithinSlidingWindow(baseTime, baseTime + 60_001L))
        assertFalse(DeduplicationHelper.isWithinSlidingWindow(baseTime, baseTime + 300_000L))
    }
}
