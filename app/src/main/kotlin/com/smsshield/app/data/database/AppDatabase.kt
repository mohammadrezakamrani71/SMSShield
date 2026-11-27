package com.smsshield.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuarantineEntity::class,
        WhitelistEntity::class,
        CustomRuleEntity::class,
        BlockedSenderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun quarantineDao(): QuarantineDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun customRuleDao(): CustomRuleDao
    abstract fun blockedSenderDao(): BlockedSenderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE quarantine_messages ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_quarantine_messages_contentHash_timestamp " +
                        "ON quarantine_messages(contentHash, timestamp)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS blocked_senders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "normalizedPattern TEXT NOT NULL, " +
                        "displayPattern TEXT NOT NULL, " +
                        "matchType TEXT NOT NULL, " +
                        "hitCount INTEGER NOT NULL DEFAULT 0, " +
                        "addedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_senders_normalizedPattern " +
                        "ON blocked_senders (normalizedPattern)"
                )
                db.execSQL(
                    "ALTER TABLE custom_rules ADD COLUMN hitCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smsshield.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
