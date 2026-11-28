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
        BlockedSenderEntity::class,
        TrainingSampleEntity::class,
        LearnedFeatureEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun quarantineDao(): QuarantineDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun customRuleDao(): CustomRuleDao
    abstract fun blockedSenderDao(): BlockedSenderDao
    abstract fun trainingSampleDao(): TrainingSampleDao
    abstract fun learnedFeatureDao(): LearnedFeatureDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE quarantine_messages ADD COLUMN feedbackState TEXT NOT NULL DEFAULT 'UNREVIEWED'"
                )
                db.execSQL(
                    "UPDATE quarantine_messages SET feedbackState = 'FALSE_POSITIVE' WHERE isFalsePositive = 1"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS training_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "contentHash TEXT NOT NULL, " +
                        "sender TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_training_samples_contentHash " +
                        "ON training_samples (contentHash)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS learned_features (" +
                        "featureKey TEXT NOT NULL PRIMARY KEY, " +
                        "featureType TEXT NOT NULL, " +
                        "spamDocCount INTEGER NOT NULL, " +
                        "safeDocCount INTEGER NOT NULL, " +
                        "weight REAL NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
