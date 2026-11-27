package com.smsshield.app.notification

import android.app.Application
import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationParserTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun buildSbn(
        packageName: String = "com.google.android.apps.messaging",
        notification: Notification,
        postTime: Long = 1_700_000_000_000L
    ): StatusBarNotification {
        return StatusBarNotification(
            packageName,
            packageName,
            1,
            null,
            1000,
            1000,
            notification,
            Process.myUserHandle(),
            null,
            postTime
        )
    }

    @Test
    fun `ignores FLAG_GROUP_SUMMARY notifications`() {
        val notification = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("30001234")
            .setContentText("فروش ویژه لغو 11")
            .setGroup("sms_group")
            .setGroupSummary(true)
            .build()

        val parsed = NotificationParser.parse(buildSbn(notification = notification))
        assertNull(parsed)
    }

    @Test
    fun `ignores non-SMS package names`() {
        val notification = NotificationCompat.Builder(context, "chat")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Friend")
            .setContentText("فروش ویژه لغو 11")
            .build()

        val parsed = NotificationParser.parse(
            buildSbn(packageName = "com.example.otherchat", notification = notification)
        )
        assertNull(parsed)
    }

    @Test
    fun `prioritizes MessagingStyle over EXTRA_BIG_TEXT and EXTRA_TEXT`() {
        val me = Person.Builder().setName("Me").build()
        val sender = Person.Builder().setName("30005555").build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage("Older message", 1_700_000_001_000L, sender)
            .addMessage("Latest messaging style body", 1_700_000_005_000L, sender)

        val notification = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("FallbackTitle")
            .setContentText("Plain text fallback")
            .setStyle(style)
            .build()
        notification.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "Big text body")

        val parsed = NotificationParser.parse(buildSbn(notification = notification))
        assertNotNull(parsed)
        assertEquals("30005555", parsed!!.sender)
        assertEquals("Latest messaging style body", parsed.body)
        assertEquals(1_700_000_005_000L, parsed.timestamp)
        assertFalse(parsed.isPossiblyTruncated)
    }

    @Test
    fun `prioritizes EXTRA_BIG_TEXT over EXTRA_TEXT when MessagingStyle is absent`() {
        val notification = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("50001234")
            .setContentText("Short text preview")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Full expanded big text body"))
            .build()

        val parsed = NotificationParser.parse(buildSbn(notification = notification))
        assertNotNull(parsed)
        assertEquals("50001234", parsed!!.sender)
        assertEquals("Full expanded big text body", parsed.body)
        assertFalse(parsed.isPossiblyTruncated)
    }

    @Test
    fun `falls back to EXTRA_TEXT when MessagingStyle and EXTRA_BIG_TEXT are absent`() {
        val notification = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("20001234")
            .setContentText("Plain extra text message")
            .build()

        val parsed = NotificationParser.parse(buildSbn(notification = notification))
        assertNotNull(parsed)
        assertEquals("20001234", parsed!!.sender)
        assertEquals("Plain extra text message", parsed.body)
        assertFalse(parsed.isPossiblyTruncated)
    }

    @Test
    fun `flags isPossiblyTruncated across MessagingStyle, EXTRA_BIG_TEXT, and EXTRA_TEXT`() {
        val me = Person.Builder().setName("Me").build()
        val sender = Person.Builder().setName("30001111").build()

        val messagingNotif = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(
                NotificationCompat.MessagingStyle(me)
                    .addMessage("پیام ناقص در مسیجینگ…", 1_700_000_010_000L, sender)
            )
            .build()
        val parsedMessaging = NotificationParser.parse(buildSbn(notification = messagingNotif))
        assertNotNull(parsedMessaging)
        assertTrue(parsedMessaging!!.isPossiblyTruncated)

        val bigTextNotif = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("30002222")
            .setStyle(NotificationCompat.BigTextStyle().bigText("متن بلند بریده‌شده..."))
            .build()
        val parsedBigText = NotificationParser.parse(buildSbn(notification = bigTextNotif))
        assertNotNull(parsedBigText)
        assertTrue(parsedBigText!!.isPossiblyTruncated)

        val plainNotif = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("30003333")
            .setContentText("متن ساده بریده‌شده…")
            .build()
        val parsedPlain = NotificationParser.parse(buildSbn(notification = plainNotif))
        assertNotNull(parsedPlain)
        assertTrue(parsedPlain!!.isPossiblyTruncated)
    }
}
