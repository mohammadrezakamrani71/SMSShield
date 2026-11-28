package com.smsshield.app.data.database

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.model.SenderMatchType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomMigrationAndRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ShieldRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ShieldRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `MIGRATION_2_3 preserves existing custom rules and creates blocked_senders table`() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration_test_v2_to_v3.db"
        context.deleteDatabase(dbName)

        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS custom_rules (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "keyword TEXT NOT NULL, " +
                            "weight INTEGER NOT NULL, " +
                            "isEnabled INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO custom_rules (keyword, weight, isEnabled) " +
                            "VALUES ('وام فوری', 30, 1)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val supportDb = FrameworkSQLiteOpenHelperFactory().create(helperConfig).writableDatabase
        AppDatabase.MIGRATION_2_3.migrate(supportDb)

        supportDb.query("SELECT keyword, weight, hitCount FROM custom_rules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("وام فوری", cursor.getString(0))
            assertEquals(30, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }

        supportDb.execSQL(
            "INSERT INTO blocked_senders (normalizedPattern, displayPattern, matchType, hitCount, addedAt) " +
                "VALUES ('550021', '550021*', 'PREFIX', 0, 1700000000000)"
        )
        supportDb.query("SELECT normalizedPattern, matchType, hitCount FROM blocked_senders").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("550021", cursor.getString(0))
            assertEquals("PREFIX", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }

        supportDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `MIGRATION_3_4 migrates quarantine feedbackState and creates training_samples and learned_features`() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration_test_v3_to_v4.db"
        context.deleteDatabase(dbName)

        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS quarantine_messages (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "dedupKey TEXT NOT NULL, " +
                            "contentHash TEXT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "sender TEXT NOT NULL, " +
                            "body TEXT NOT NULL, " +
                            "score INTEGER NOT NULL, " +
                            "verdict TEXT NOT NULL, " +
                            "reasonsJson TEXT NOT NULL, " +
                            "timestamp INTEGER NOT NULL, " +
                            "wasDismissed INTEGER NOT NULL, " +
                            "isFalsePositive INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO quarantine_messages (dedupKey, contentHash, packageName, sender, body, score, verdict, reasonsJson, timestamp, wasDismissed, isFalsePositive) " +
                            "VALUES ('k1', 'h1', 'pkg', '30001', 'safe msg', 80, 'SPAM', '[]', 1000, 1, 1)"
                    )
                    db.execSQL(
                        "INSERT INTO quarantine_messages (dedupKey, contentHash, packageName, sender, body, score, verdict, reasonsJson, timestamp, wasDismissed, isFalsePositive) " +
                            "VALUES ('k2', 'h2', 'pkg', '30002', 'spam msg', 90, 'SPAM', '[]', 2000, 1, 0)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val supportDb = FrameworkSQLiteOpenHelperFactory().create(helperConfig).writableDatabase
        AppDatabase.MIGRATION_3_4.migrate(supportDb)

        supportDb.query("SELECT dedupKey, feedbackState FROM quarantine_messages ORDER BY id ASC").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("k1", cursor.getString(0))
            assertEquals("FALSE_POSITIVE", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("k2", cursor.getString(0))
            assertEquals("UNREVIEWED", cursor.getString(1))
        }

        supportDb.execSQL(
            "INSERT INTO training_samples (contentHash, sender, body, label, source, createdAt) " +
                "VALUES ('th1', '30001', 'کلینیک زیبایی', 'SPAM', 'MANUAL', 1700000000000)"
        )
        supportDb.execSQL(
            "INSERT INTO learned_features (featureKey, featureType, spamDocCount, safeDocCount, weight, updatedAt) " +
                "VALUES ('کلینیک زیبایی', 'BIGRAM', 3, 0, 1.8, 1700000000000)"
        )

        supportDb.query("SELECT COUNT(*) FROM training_samples").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        supportDb.query("SELECT COUNT(*) FROM learned_features").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        supportDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `MIGRATION_4_5 defaults existing rows to SPAM_NUISANCE and supports OFFER with discountCode`() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration_test_v4_to_v5.db"
        context.deleteDatabase(dbName)

        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS quarantine_messages (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "dedupKey TEXT NOT NULL, " +
                            "contentHash TEXT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "sender TEXT NOT NULL, " +
                            "body TEXT NOT NULL, " +
                            "score INTEGER NOT NULL, " +
                            "verdict TEXT NOT NULL, " +
                            "reasonsJson TEXT NOT NULL, " +
                            "timestamp INTEGER NOT NULL, " +
                            "wasDismissed INTEGER NOT NULL, " +
                            "isFalsePositive INTEGER NOT NULL, " +
                            "feedbackState TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO quarantine_messages (dedupKey, contentHash, packageName, sender, body, score, verdict, reasonsJson, timestamp, wasDismissed, isFalsePositive, feedbackState) " +
                            "VALUES ('old_k1', 'old_h1', 'pkg', '50001', 'وام فوری', 85, 'SPAM', '[]', 1000, 1, 0, 'UNREVIEWED')"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val supportDb = FrameworkSQLiteOpenHelperFactory().create(helperConfig).writableDatabase
        AppDatabase.MIGRATION_4_5.migrate(supportDb)

        supportDb.query("SELECT dedupKey, category, discountCode FROM quarantine_messages WHERE dedupKey = 'old_k1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("old_k1", cursor.getString(0))
            assertEquals("SPAM_NUISANCE", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }

        supportDb.execSQL(
            "INSERT INTO quarantine_messages (dedupKey, contentHash, packageName, sender, body, score, verdict, reasonsJson, timestamp, wasDismissed, isFalsePositive, feedbackState, category, discountCode) " +
                "VALUES ('new_k2', 'new_h2', 'pkg', '30001', 'تخفیف بلک فرایدی کد تخفیف BF50', 80, 'SPAM', '[]', 2000, 1, 0, 'UNREVIEWED', 'OFFER', 'BF50')"
        )

        supportDb.query("SELECT dedupKey, category, discountCode FROM quarantine_messages WHERE dedupKey = 'new_k2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("new_k2", cursor.getString(0))
            assertEquals("OFFER", cursor.getString(1))
            assertEquals("BF50", cursor.getString(2))
        }

        supportDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `ShieldPreferences recordSmsDiagnostic increments count and persists last package and outcome`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = ShieldPreferences(context)

        val initialCount = prefs.getDiagnostics().totalSmsNotificationsSeen
        prefs.setListenerConnected(true)
        prefs.recordSmsDiagnostic(
            packageName = "com.google.android.apps.messaging",
            timestamp = 1_700_000_123_000L,
            outcome = "قرنطینه شد (تخفیف • امتیاز 80)"
        )

        val updated = prefs.getDiagnostics()
        assertTrue(updated.isListenerConnected)
        assertEquals(initialCount + 1, updated.totalSmsNotificationsSeen)
        assertEquals("com.google.android.apps.messaging", updated.lastSmsPackage)
        assertEquals(1_700_000_123_000L, updated.lastSmsTimestamp)
        assertEquals("قرنطینه شد (تخفیف • امتیاز 80)", updated.lastSmsOutcome)
        assertEquals(updated, prefs.diagnosticsFlow.value)
    }

    @Test
    fun `quarantine feedback transitions are reversible and never lock whitelist or delete`() = runTest {
        val insertedId = repository.insertQuarantine(
            QuarantineEntity(
                dedupKey = "dk1",
                contentHash = "ch1",
                packageName = "com.google.android.apps.messaging",
                sender = "+9830005555",
                body = "پیامک نمونه برای تست بازخورد",
                score = 80,
                verdict = "SPAM",
                reasonsJson = "[]",
                timestamp = 1_700_000_000_000L,
                wasDismissed = true
            )
        )

        // 1. Mark FALSE_POSITIVE
        repository.setQuarantineFeedback(insertedId, QuarantineFeedbackState.FALSE_POSITIVE)
        var msg = repository.observeQuarantineMessages().first().first()
        assertEquals(QuarantineFeedbackState.FALSE_POSITIVE, msg.feedbackState)
        assertTrue(msg.isFalsePositive)
        assertEquals(1, repository.observeTrainingSamples().first().size)

        // 2. Switch freely to CONFIRMED_SPAM
        repository.setQuarantineFeedback(insertedId, QuarantineFeedbackState.CONFIRMED_SPAM)
        msg = repository.observeQuarantineMessages().first().first()
        assertEquals(QuarantineFeedbackState.CONFIRMED_SPAM, msg.feedbackState)
        assertFalse(msg.isFalsePositive)
        assertEquals("SPAM", repository.observeTrainingSamples().first().first().label)

        // 3. Revert to UNREVIEWED (removes auto-sample)
        repository.setQuarantineFeedback(insertedId, QuarantineFeedbackState.UNREVIEWED)
        msg = repository.observeQuarantineMessages().first().first()
        assertEquals(QuarantineFeedbackState.UNREVIEWED, msg.feedbackState)
        assertTrue(repository.observeTrainingSamples().first().isEmpty())

        // 4. Independent sender whitelist still works cleanly
        repository.addWhitelistedSender(msg.sender, msg.sender)
        assertTrue("30005555" in repository.getWhitelistedSenderSet())

        // 5. Delete quarantine message cleanly
        repository.deleteQuarantineMessageById(insertedId)
        assertTrue(repository.observeQuarantineMessages().first().isEmpty())
    }

    @Test
    fun `blacklist and whitelist resolve conflicts mutually`() = runTest {
        repository.addWhitelistedSender("+98550021", "550021")
        assertTrue("550021" in repository.getWhitelistedSenderSet())

        repository.addBlockedSender("0098550021", SenderMatchType.EXACT)
        assertFalse("550021" in repository.getWhitelistedSenderSet())
        assertEquals(1, repository.getBlockedSenderRules().size)
        assertEquals("550021", repository.getBlockedSenderRules().first().pattern)

        repository.addWhitelistedSender("۵۵۰۰۲۱", "550021")
        assertTrue("550021" in repository.getWhitelistedSenderSet())
        assertTrue(repository.getBlockedSenderRules().isEmpty())
    }

    @Test
    fun `selective pack import skips duplicates and hit tracking increments atomically`() = runTest {
        repository.addCustomRule("وام فوری", 30)
        repository.addBlockedSender("550021", SenderMatchType.EXACT)

        val summary = repository.importSelectedPackItems(
            keywords = listOf(
                KeywordRuleItem("وام فوری", 25),
                KeywordRuleItem("کلینیک زیبایی", 25)
            ),
            blockedSenders = listOf(
                BlockedSenderItem("+98550021", SenderMatchType.EXACT),
                BlockedSenderItem("9810008500*", SenderMatchType.PREFIX)
            )
        )

        assertEquals(1, summary.addedKeywords)
        assertEquals(1, summary.addedBlockedSenders)

        repository.recordMatchHits(
            matchedCustomKeywords = listOf("وام فوری", "کلینیک زیبایی"),
            matchedBlockedSenderPattern = "550021"
        )
        assertEquals(3, repository.observeTotalUserHits().first())
    }

    @Test
    fun `backup export and restore merges cleanly without removing existing rules`() = runTest {
        repository.addCustomRule("وام فوری", 30)
        repository.addBlockedSender("550021*", SenderMatchType.PREFIX)
        repository.addWhitelistedSender("30009999", "Bank")

        val backup = repository.exportFullBackup(spamThreshold = 80, themeMode = ThemeMode.DARK)
        assertEquals(80, backup.spamThreshold)
        assertEquals("DARK", backup.themeMode)
        assertEquals(1, backup.customRules.size)
        assertEquals(1, backup.blockedSenders.size)
        assertEquals(1, backup.whitelistSenders.size)

        repository.addCustomRule("کلیدواژه محلی", 20)
        val restoreSummary = repository.restoreFullBackup(backup)
        assertEquals(0, restoreSummary.addedKeywords)
        assertEquals(0, restoreSummary.addedBlockedSenders)
        assertEquals(0, restoreSummary.addedWhitelist)
        assertEquals(ThemeMode.DARK, restoreSummary.restoredThemeMode)
        assertEquals(2, repository.getEnabledCustomRules().size)
    }

    @Test
    fun `quarantine entity supports OFFER category and nullable discountCode`() = runTest {
        repository.insertQuarantine(
            QuarantineEntity(
                dedupKey = "offer_dk",
                contentHash = "offer_ch",
                packageName = "com.google.android.apps.messaging",
                sender = "30001234",
                body = "تخفیف بلک فرایدی کد تخفیف BF40",
                score = 80,
                verdict = "SPAM",
                reasonsJson = "[]",
                timestamp = 1_700_000_000_000L,
                wasDismissed = true,
                category = "OFFER",
                discountCode = "BF40"
            )
        )
        val saved = repository.observeQuarantineMessages().first().first()
        assertEquals("OFFER", saved.category)
        assertEquals("BF40", saved.discountCode)
    }
}
