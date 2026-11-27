package com.smsshield.app.data.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.model.SenderMatchType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `custom_rules` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`keyword` TEXT NOT NULL, " +
                            "`weight` INTEGER NOT NULL, " +
                            "`isEnabled` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO `custom_rules` (`keyword`, `weight`, `isEnabled`) " +
                            "VALUES ('وام فوری', 30, 1)"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
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

        val backup = repository.exportFullBackup(spamThreshold = 80)
        assertEquals(80, backup.spamThreshold)
        assertEquals(1, backup.customRules.size)
        assertEquals(1, backup.blockedSenders.size)
        assertEquals(1, backup.whitelistSenders.size)

        repository.addCustomRule("کلیدواژه محلی", 20)
        val restoreSummary = repository.restoreFullBackup(backup)
        assertEquals(0, restoreSummary.addedKeywords)
        assertEquals(0, restoreSummary.addedBlockedSenders)
        assertEquals(0, restoreSummary.addedWhitelist)
        assertEquals(2, repository.getEnabledCustomRules().size)
    }
}
